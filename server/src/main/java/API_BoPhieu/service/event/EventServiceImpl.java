package API_BoPhieu.service.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import API_BoPhieu.constants.EventManagement;
import API_BoPhieu.constants.EventStatus;
import API_BoPhieu.dto.common.PageResponse;
import API_BoPhieu.dto.event.EventCountersResponse;
import API_BoPhieu.dto.event.EventDetailResponse;
import API_BoPhieu.dto.event.EventDto;
import API_BoPhieu.dto.event.EventPageWithCountersResponse;
import API_BoPhieu.dto.event.EventResponse;
import API_BoPhieu.dto.event.ManagerInfo;
import API_BoPhieu.dto.event.ParticipantInfo;
import API_BoPhieu.dto.event.SecretaryInfo;
import API_BoPhieu.entity.Attendant;
import API_BoPhieu.entity.Event;
import API_BoPhieu.entity.EventManager;
import API_BoPhieu.entity.User;
import API_BoPhieu.exception.AuthException;
import API_BoPhieu.exception.ConflictException;
import API_BoPhieu.exception.EventException;
import API_BoPhieu.exception.NotFoundException;
import API_BoPhieu.mapper.EventMapper;
import API_BoPhieu.repository.AttendantRepository;
import API_BoPhieu.repository.EventManagerRepository;
import API_BoPhieu.repository.EventRepository;
import API_BoPhieu.repository.UserRepository;
import API_BoPhieu.service.attendant.QRCodeService;
import API_BoPhieu.service.email.EmailService;
import API_BoPhieu.service.file.FileStorageService;
import API_BoPhieu.service.sse.event_list.EventListSseService;
import API_BoPhieu.specification.EventSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final FileStorageService fileStorageService;
    private final AttendantRepository attendantRepository;
    private final QRCodeService qrCodeService;
    private final UserRepository userRepository;
    private final EventManagerRepository eventManagerRepository;
    private final EventListSseService eventListSseService;

    private final EmailService emailService;

    @Override
    @Transactional
    public EventResponse createEvent(EventDto eventDto, String creatorEmail) {
        User user = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new AuthException("Người dùng không hợp lệ!"));

        Event newEvent = eventMapper.toEntity(eventDto);
        newEvent.setQrJoinToken(qrCodeService.generateQRToken());
        newEvent.setCreateBy(user.getId());
        newEvent = eventRepository.save(newEvent);

        log.info("Sự kiện '{}' đã được tạo bởi người dùng '{}'", newEvent.getTitle(), creatorEmail);

        eventListSseService.dispatchListUpdate();

        EventResponse eventResponse = eventMapper.toEventResponse(newEvent);
        eventResponse.setCurrentParticipants(0);
        return eventResponse;
    }

    @Override
    @Transactional
    public EventResponse updateEvent(Integer eventId, EventDto eventDto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Người dùng không hợp lệ!"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventException("Không tìm thấy sự kiện!"));

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        boolean isManager = eventManagerRepository.findByUserIdAndEventId(user.getId(), eventId)
                .map(manager -> manager.getRoleType() == EventManagement.MANAGE).orElse(false);

        if (!isAdmin && !isManager) {
            throw new EventException("Bạn không có quyền chỉnh sửa sự kiện này");
        }

        log.debug("Bắt đầu cập nhật sự kiện ID: {} với dữ liệu DTO", eventId);

        event.setTitle(eventDto.getTitle());
        event.setDescription(eventDto.getDescription());
        event.setStartTime(eventDto.getStartTime());
        event.setEndTime(eventDto.getEndTime());
        event.setLocation(eventDto.getLocation());
        event.setMaxParticipants(eventDto.getMaxParticipants());
        event.setUrlDocs(eventDto.getUrlDocs());

        // Kiểm tra và cập nhật trạng thái dựa trên ngày bắt đầu và kết thúc
        // Chỉ cập nhật nếu sự kiện chưa bị hủy
        if (event.getStatus() != EventStatus.CANCELLED) {
            Instant now = Instant.now();
            if (event.getEndTime() != null
                    && (event.getEndTime().isBefore(now) || event.getEndTime().equals(now))) {
                // Sự kiện đã kết thúc
                event.setStatus(EventStatus.COMPLETED);
                log.debug("Cập nhật trạng thái sự kiện ID {} thành COMPLETED (endTime <= now)",
                        eventId);
            } else if (event.getStartTime() != null
                    && (event.getStartTime().isBefore(now) || event.getStartTime().equals(now))
                    && (event.getEndTime() == null || event.getEndTime().isAfter(now))) {
                // Sự kiện đang diễn ra
                event.setStatus(EventStatus.ONGOING);
                log.debug(
                        "Cập nhật trạng thái sự kiện ID {} thành ONGOING (startTime <= now < endTime)",
                        eventId);
            } else if (event.getStartTime() != null && event.getStartTime().isAfter(now)) {
                // Sự kiện sắp diễn ra
                event.setStatus(EventStatus.UPCOMING);
                log.debug("Cập nhật trạng thái sự kiện ID {} thành UPCOMING (startTime > now)",
                        eventId);
            }
        }

        event = eventRepository.save(event);

        eventListSseService.dispatchListUpdate();

        EventResponse eventResponse = eventMapper.toEventResponse(event);
        eventResponse.setCurrentParticipants(attendantRepository.countByEventId(eventId));

        return eventResponse;
    }

    @Override
    @Transactional
    public EventResponse uploadBanner(Integer eventId, MultipartFile file) {
        log.info("Bắt đầu quá trình upload banner cho sự kiện ID: {}", eventId);

        Event event = eventRepository.findById(eventId).orElseThrow(() -> {
            log.error("Upload banner thất bại: Không tìm thấy sự kiện ID {}", eventId);
            return new NotFoundException("Không tìm thấy sự kiện với ID: " + eventId);
        });

        if (event.getBanner() != null && !event.getBanner().isEmpty()) {
            try {
                String oldBannerKey = event.getBanner();
                fileStorageService.deleteFile(oldBannerKey);
                log.debug("Đã xóa banner cũ thành công: {}", oldBannerKey);
            } catch (Exception e) {
                log.warn("Không thể xóa banner cũ cho sự kiện ID {}: {}", eventId, e.getMessage());
            }
        }

        String baseName = "banner_event_" + eventId;
        String newBannerKey = fileStorageService.storeFile(file, "banners", baseName);

        event.setBanner(newBannerKey);
        event = eventRepository.save(event);

        eventListSseService.dispatchListUpdate();
        log.info("Upload và cập nhật banner thành công cho sự kiện ID {}. Banner mới: {}", eventId,
                newBannerKey);

        EventResponse eventResponse = eventMapper.toEventResponse(event);
        eventResponse.setCurrentParticipants(attendantRepository.countByEventId(eventId));
        return eventResponse;
    }

    @Override
    public byte[] generateEventQRCode(Integer eventId, String baseUrl, String creatorEmail)
            throws Exception {
        User user = userRepository.findByEmail(creatorEmail).orElseThrow(
                () -> new AuthException("Không tìm thấy người dùng với email: " + creatorEmail));

        if (eventManagerRepository.findByUserIdAndEventId(user.getId(), eventId).isEmpty()) {
            throw new AuthException("Bạn không có quyền tạo mã QR cho sự kiện này");
        }

        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new NotFoundException("Không tìm thấy sự kiện với ID: " + eventId));
        String joinUrl = baseUrl + "/events/join/" + event.getQrJoinToken();
        return qrCodeService.generateQRCode(joinUrl);
    }

    @Override
    @Transactional
    public Attendant joinEvent(String eventToken, String creatorEmail) {
        User user = userRepository.findByEmail(creatorEmail).orElseThrow(
                () -> new AuthException("Không tìm thấy người dùng với email: " + creatorEmail));

        Event event = eventRepository.findByQrJoinToken(eventToken).orElseThrow(
                () -> new NotFoundException("Không tìm thấy sự kiện với mã token: " + eventToken));

        if (attendantRepository.existsByUserIdAndEventId(event.getId(), user.getId())) {
            throw new ConflictException("Bạn đã tham gia sự kiện này rồi");
        }

        Integer currentParticipants = attendantRepository.countByEventId(event.getId());
        if (event.getMaxParticipants() != null
                && currentParticipants >= event.getMaxParticipants()) {
            throw new ConflictException("Sự kiện đã đạt số lượng người tham gia tối đa");
        }

        if (event.getStatus() != EventStatus.UPCOMING) {
            throw new EventException(
                    "Sự kiện không còn khả dụng để tham gia. Chỉ có thể tham gia các sự kiện sắp diễn ra");
        }

        Attendant newAttendant = new Attendant();
        newAttendant.setEventId(event.getId());
        newAttendant.setUserId(user.getId());

        Attendant savedAttendant = attendantRepository.save(newAttendant);

        log.info("Người dùng '{}' đã tham gia sự kiện '{}'", user.getEmail(), event.getTitle());

        try {
            emailService.sendEventJoinNotificationEmail(user, event);
            log.debug("Đã gửi email thông báo tham gia sự kiện cho người dùng '{}'",
                    user.getEmail());
        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo tham gia sự kiện cho người dùng '{}': {}",
                    user.getEmail(), e.getMessage());
        }

        return savedAttendant;
    }

    @Override
    public EventDetailResponse getEventById(Integer eventId, String email) {
        log.info("Bắt đầu lấy chi tiết sự kiện ID: {} cho người dùng '{}'", eventId, email);

        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new NotFoundException("Không tìm thấy sự kiện với ID: " + eventId));

        User currentUser = userRepository.findByEmail(email).orElseThrow(
                () -> new AuthException("Không tìm thấy người dùng với email: " + email));

        List<Attendant> attendants = attendantRepository.findByEventId(eventId);
        List<EventManager> eventManagers = eventManagerRepository.findByEventId(eventId);

        log.debug("Đã tìm thấy {} người tham gia và {} quản lý cho sự kiện ID: {}",
                attendants.size(), eventManagers.size(), eventId);

        Set<Integer> userIds =
                attendants.stream().map(Attendant::getUserId).collect(Collectors.toSet());
        eventManagers.forEach(em -> userIds.add(em.getUserId()));

        log.debug("Thực hiện truy vấn hàng loạt cho {} user ID.", userIds.size());

        final Map<Integer, User> userMap = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(new ArrayList<>(userIds)).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

        boolean isUserRegistered =
                attendants.stream().anyMatch(a -> a.getUserId().equals(currentUser.getId()));

        boolean isUserCheckedIn = attendants.stream().anyMatch(
                a -> a.getUserId().equals(currentUser.getId()) && a.getCheckedTime() != null);

        List<ParticipantInfo> participants = attendants.stream().map(
                attendant -> mapToParticipantInfo(attendant, userMap.get(attendant.getUserId())))
                .collect(Collectors.toList());

        List<ManagerInfo> managerInfos =
                eventManagers.stream().filter(em -> em.getRoleType() == EventManagement.MANAGE)
                        .map(manager -> mapToManagerInfo(userMap.get(manager.getUserId())))
                        .collect(Collectors.toList());

        List<SecretaryInfo> secretaryInfos =
                eventManagers.stream().filter(em -> em.getRoleType() == EventManagement.STAFF)
                        .map(secretary -> mapToSecretaryInfo(userMap.get(secretary.getUserId())))
                        .collect(Collectors.toList());

        return eventMapper.toEventDetailResponse(event, isUserRegistered, isUserCheckedIn,
                participants, managerInfos, secretaryInfos);
    }

    @Override
    public EventPageWithCountersResponse getAllEvents(int page, int size, String sortBy,
            String sortDir, EventStatus status, String search, String email) {
        return fetchEventsFromDatabase(page, size, sortBy, sortDir, status, search, email);
    }

    private EventPageWithCountersResponse fetchEventsFromDatabase(int page, int size, String sortBy,
            String sortDir, EventStatus status, String search, String email) {
        log.debug(
                "Fetching events from DB - page: {}, size: {}, sortBy: {}, sortDir: {}, status: {}, search: {}, email: {}",
                page, size, sortBy, sortDir, status, search, email);
        Optional<User> userOptional =
                (email != null && !email.isBlank()) ? userRepository.findByEmail(email)
                        : Optional.empty();
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Specification<Event> spec = Specification.where(EventSpecification.hasStatus(status))
                .and(EventSpecification.searchByKeyword(search));
        Page<Event> eventPage = eventRepository.findAll(spec, pageable);
        PageResponse<EventResponse> pageResponse = createPageResponse(eventPage, userOptional);
        EventCountersResponse counters = createCountersResponse(userOptional);
        return EventPageWithCountersResponse.builder().pagination(pageResponse).counters(counters)
                .build();
    }

    @Override
    @Transactional
    public void cancelEvent(Integer id) {
        log.debug("Nhận yêu cầu hủy sự kiện với ID: {}", id);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sự kiện với ID: " + id));
        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);

        eventListSseService.dispatchListUpdate();
        log.info("Sự kiện '{}' (ID: {}) đã được hủy.", event.getTitle(), id);
    }

    @Override
    public EventPageWithCountersResponse getManagedEvents(int page, int size, String sortBy,
            String sortDir, EventStatus status, String search, String email) {

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new AuthException("Không tìm thấy người dùng với email: " + email));

        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<Event> spec = Specification
                .where(EventSpecification.isManagedByUserExists(user.getId()))
                .and(EventSpecification.isNotCancelled()).and(EventSpecification.hasStatus(status))
                .and(EventSpecification.searchByKeyword(search));

        Page<Event> eventPage = eventRepository.findAll(spec, pageable);

        PageResponse<EventResponse> pageResponse = createPageResponse(eventPage, Optional.of(user));
        EventCountersResponse counters = createManagedCountersResponse(user);

        return EventPageWithCountersResponse.builder().pagination(pageResponse).counters(counters)
                .build();
    }

    private ManagerInfo mapToManagerInfo(User user) {
        if (user == null)
            return new ManagerInfo();
        return ManagerInfo.builder().userId(user.getId()).userName(user.getName())
                .userEmail(user.getEmail()).build();
    }

    private SecretaryInfo mapToSecretaryInfo(User user) {
        if (user == null)
            return new SecretaryInfo();
        return SecretaryInfo.builder().userId(user.getId()).userName(user.getName())
                .userEmail(user.getEmail()).build();
    }

    private ParticipantInfo mapToParticipantInfo(Attendant attendant, User user) {
        return ParticipantInfo.builder().userId(attendant.getUserId())
                .userName(user != null ? user.getName() : "Unknown User")
                .userEmail(user != null ? user.getEmail() : "unknown.email@example.com")
                .joinedAt(attendant.getJoinedAt()).checkedTime(attendant.getCheckedTime())
                .isCheckedIn(attendant.getCheckedTime() != null).build();
    }

    private PageResponse<EventResponse> createPageResponse(Page<Event> eventPage,
            Optional<User> userOptional) {
        if (eventPage.isEmpty()) {
            return new PageResponse<>(Page.empty());
        }

        List<Event> events = eventPage.getContent();
        List<Integer> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());

        Set<Integer> creatorIds =
                events.stream().map(Event::getCreateBy).collect(Collectors.toSet());
        List<EventManager> allManagersOnPage = eventManagerRepository.findAllByEventIdIn(eventIds);
        Set<Integer> managerUserIds =
                allManagersOnPage.stream().map(EventManager::getUserId).collect(Collectors.toSet());

        Set<Integer> allUserIdsToFetch = new HashSet<>(creatorIds);
        allUserIdsToFetch.addAll(managerUserIds);

        Map<Integer, User> userMap = userRepository.findAllById(allUserIdsToFetch).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Integer, Long> participantCounts = attendantRepository
                .countParticipantsByEventIds(eventIds).stream().collect(Collectors
                        .toMap(result -> (Integer) result[0], result -> (Long) result[1]));

        Set<Integer> registeredEventIds = userOptional.map(
                user -> attendantRepository.findRegisteredEventIdsByUserId(user.getId(), eventIds))
                .orElse(Collections.emptySet());

        Page<EventResponse> responsePage = eventPage.map(event -> {
            EventResponse eventResponse = eventMapper.toEventResponse(event);
            eventResponse.setCurrentParticipants(
                    participantCounts.getOrDefault(event.getId(), 0L).intValue());
            eventResponse.setIsRegistered(registeredEventIds.contains(event.getId()));

            User creator = userMap.get(event.getCreateBy());
            if (creator != null) {
                eventResponse.setCreatedById(creator.getId());
                eventResponse.setCreatedByName(creator.getName());
            }

            allManagersOnPage.stream()
                    .filter(manager -> manager.getEventId().equals(event.getId())
                            && manager.getRoleType() == EventManagement.MANAGE)
                    .findFirst().ifPresent(manager -> {
                        User managerUser = userMap.get(manager.getUserId());
                        if (managerUser != null) {
                            eventResponse.setManagerId(managerUser.getId());
                            eventResponse.setManagerName(managerUser.getName());
                        }
                    });

            return eventResponse;
        });

        return new PageResponse<>(responsePage);
    }

    private EventCountersResponse createCountersResponse(Optional<User> userOptional) {
        Map<EventStatus, Long> statusCounts = new EnumMap<>(EventStatus.class);
        eventRepository.countEventsByStatus().forEach(result -> {
            statusCounts.put((EventStatus) result[0], (Long) result[1]);
        });

        EventCountersResponse.EventCountersResponseBuilder builder = EventCountersResponse.builder()
                .upcoming(statusCounts.getOrDefault(EventStatus.UPCOMING, 0L))
                .ongoing(statusCounts.getOrDefault(EventStatus.ONGOING, 0L))
                .completed(statusCounts.getOrDefault(EventStatus.COMPLETED, 0L))
                .cancelled(statusCounts.getOrDefault(EventStatus.CANCELLED, 0L));

        userOptional.ifPresent(user -> {
            long managedCount = eventRepository.countManagedEventsByUserId(user.getId());
            builder.manage(managedCount);
        });

        return builder.build();
    }

    private EventCountersResponse createManagedCountersResponse(User user) {
        Map<EventStatus, Long> statusCounts = new EnumMap<>(EventStatus.class);
        // Đếm sự kiện theo status chỉ cho sự kiện mà user quản lý
        eventRepository.countManagedEventsByStatus(user.getId()).forEach(result -> {
            statusCounts.put((EventStatus) result[0], (Long) result[1]);
        });

        long managedCount = eventRepository.countManagedEventsByUserId(user.getId());

        return EventCountersResponse.builder()
                .upcoming(statusCounts.getOrDefault(EventStatus.UPCOMING, 0L))
                .ongoing(statusCounts.getOrDefault(EventStatus.ONGOING, 0L))
                .completed(statusCounts.getOrDefault(EventStatus.COMPLETED, 0L))
                .cancelled(statusCounts.getOrDefault(EventStatus.CANCELLED, 0L))
                .manage(managedCount).build();
    }
}
