package API_BoPhieu.service.attendant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import API_BoPhieu.constants.EventManagement;
import API_BoPhieu.constants.EventStatus;
import API_BoPhieu.constants.ImportJobStatus;
import API_BoPhieu.dto.attendant.ParticipantDto;
import API_BoPhieu.dto.attendant.ParticipantResponse;
import API_BoPhieu.dto.attendant.ParticipantsDto;
import API_BoPhieu.dto.unit.UnitResponseDTO;
import API_BoPhieu.dto.user.UserResponseDTO;
import API_BoPhieu.entity.Attendant;
import API_BoPhieu.entity.Event;
import API_BoPhieu.entity.EventManager;
import API_BoPhieu.entity.Unit;
import API_BoPhieu.entity.User;
import API_BoPhieu.exception.AuthException;
import API_BoPhieu.exception.ConflictException;
import API_BoPhieu.exception.NotFoundException;
import API_BoPhieu.repository.AttendantRepository;
import API_BoPhieu.repository.EventManagerRepository;
import API_BoPhieu.repository.EventRepository;
import API_BoPhieu.repository.UnitRepository;
import API_BoPhieu.repository.UserRepository;
import API_BoPhieu.service.email.EmailService;
import API_BoPhieu.service.file.FileExportService;
import API_BoPhieu.service.file.FileImportService;
import API_BoPhieu.service.import_job.ImportJobService;
import API_BoPhieu.service.sse.check_in.CheckInSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AttendantServiceImpl implements AttendantService {

    private final AttendantRepository attendantRepository;
    private final CheckInSseService sseService;
    private final QRCodeService qrCodeService;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventManagerRepository eventManagerRepository;
    private final UnitRepository unitRepository;
    private final EmailService emailService;
    private final FileImportService fileImportService;
    private final FileExportService fileExportService;
    private final ImportJobService importJobService;
    private final ObjectMapper objectMapper;

    @Value("${api.prefix}")
    private String apiPrefix;

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> getParticipantByEventId(Integer eventId) {
        log.debug("Bắt đầu lấy danh sách người tham gia cho sự kiện ID: {}", eventId);
        List<Attendant> attendants = attendantRepository.findByEventId(eventId);
        if (attendants.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Integer> userIds =
                attendants.stream().map(Attendant::getUserId).collect(Collectors.toSet());

        Map<Integer, User> userMap = userRepository.findAllById(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Set<Integer> unitIds = userMap.values().stream().map(User::getUnitId)
                .filter(id -> id != null).collect(Collectors.toSet());

        Map<Integer, Unit> unitMap = Collections.emptyMap();
        if (!unitIds.isEmpty()) {
            unitMap = unitRepository.findAllById(new ArrayList<>(unitIds)).stream()
                    .collect(Collectors.toMap(Unit::getId, unit -> unit));
        }

        final Map<Integer, Unit> finalUnitMap = unitMap;

        return attendants.stream().map(attendant -> {
            User user = userMap.get(attendant.getUserId());
            if (user == null) {
                return null;
            }
            Unit unit = (user.getUnitId() != null) ? finalUnitMap.get(user.getUnitId()) : null;
            return mapToParticipantResponse(attendant, user, unit);
        }).filter(response -> response != null).collect(Collectors.toList());
    }

    @Override
    public Attendant checkIn(String eventToken, String userEmail) {
        log.debug("Bắt đầu check-in cho người dùng '{}' với event token '{}'", userEmail,
                eventToken);

        User user = userRepository.findByEmail(userEmail).orElseThrow(
                () -> new NotFoundException("Không tìm thấy người dùng với email: " + userEmail));

        Event event = eventRepository.findByQrJoinToken(eventToken).orElseThrow(
                () -> new NotFoundException("Không tìm thấy sự kiện với mã QR: " + eventToken));

        Attendant attendant =
                attendantRepository.findByUserIdAndEventId(user.getId(), event.getId()).orElseThrow(
                        () -> new NotFoundException("Bạn chưa đăng ký tham gia sự kiện này"));

        if (attendant.getCheckedTime() != null) {
            log.warn(
                    "Người dùng '{}' cố gắng check-in lại sự kiện '{}' trong khi đã check-in từ trước.",
                    userEmail, event.getTitle());
            throw new ConflictException("Bạn đã check-in sự kiện này rồi");
        }

        attendant.setCheckedTime(Instant.now());
        Attendant updatedAttendant = attendantRepository.save(attendant);

        log.info("Người dùng '{}' (ID: {}) đã check-in thành công sự kiện '{}' (ID: {})",
                user.getEmail(), user.getId(), event.getTitle(), event.getId());

        ParticipantResponse response = mapToParticipantResponse(updatedAttendant, user);
        sseService.sendEventToClients(event.getId(), "participant-checked-in", response);

        return updatedAttendant;
    }

    @Override
    public byte[] generateQrCheck(Integer eventId) throws Exception {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sự kiện ID: " + eventId));
        String checkInUrl = apiPrefix + "/attendants/check-in/" + event.getQrJoinToken();
        log.debug("Tạo QR code cho URL check-in: {}", checkInUrl);
        return qrCodeService.generateQRCode(checkInUrl);
    }

    @Override
    public void deleteParticipantByEventIdAndUserId(Integer eventId, Integer userId) {
        log.debug("Bắt đầu xóa người tham gia ID {} khỏi sự kiện ID {}", userId, eventId);
        Attendant attendant =
                attendantRepository.findByUserIdAndEventId(userId, eventId).orElseThrow(
                        () -> new NotFoundException("Người tham gia không tồn tại trong sự kiện."));
        attendantRepository.delete(attendant);
        log.info("Đã xóa thành công người tham gia ID {} khỏi sự kiện ID {}", userId, eventId);

    }

    @Override
    public List<ParticipantResponse> addParticipants(Integer eventId,
            ParticipantsDto participantsDto, String adderEmail) {
        log.info("Người dùng '{}' bắt đầu quá trình thêm {} người tham gia vào sự kiện ID {}",
                adderEmail, participantsDto.getEmails().size(), eventId);

        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new NotFoundException("Không tìm thấy sự kiện với ID: " + eventId));

        if (event.getStartTime().isBefore(Instant.now())) {
            log.warn("Thêm người tham gia thất bại: Sự kiện ID {} đã bắt đầu hoặc kết thúc.",
                    eventId);
            throw new ConflictException(
                    "Sự kiện đã bắt đầu hoặc kết thúc, không thể thêm người tham gia");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            log.warn("Thêm người tham gia thất bại: Sự kiện ID {} đã bị hủy.", eventId);
            throw new ConflictException("Sự kiện đã bị hủy, không thể thêm người tham gia");
        }

        List<String> emails = participantsDto.getEmails().stream().map(ParticipantDto::getEmail)
                .collect(Collectors.toList());

        if (emails.isEmpty()) {
            log.debug("Danh sách email thêm vào trống, không thực hiện hành động nào.");
            return Collections.emptyList();
        }

        List<User> usersToAdd = userRepository.findAllByEmailIn(emails);
        if (usersToAdd.size() != emails.size()) {
            log.warn(
                    "Thêm người tham gia thất bại: Một hoặc nhiều email không tồn tại trong hệ thống.");
            throw new NotFoundException("Một hoặc nhiều email không tồn tại trong hệ thống");
        }

        List<Integer> userIdsToAdd =
                usersToAdd.stream().map(User::getId).collect(Collectors.toList());

        Set<Integer> existingParticipantIds =
                attendantRepository.findAllByEventIdAndUserIdIn(eventId, userIdsToAdd).stream()
                        .map(Attendant::getUserId).collect(Collectors.toSet());

        List<User> newParticipants =
                usersToAdd.stream().filter(user -> !existingParticipantIds.contains(user.getId()))
                        .collect(Collectors.toList());

        if (newParticipants.isEmpty()) {
            log.info(
                    "Tất cả người dùng trong danh sách đã tham gia sự kiện ID {}, không có ai được thêm mới.",
                    eventId);
            return Collections.emptyList();
        }

        int currentParticipantsCount = attendantRepository.countByEventId(eventId);
        if (event.getMaxParticipants() != null
                && currentParticipantsCount + newParticipants.size() > event.getMaxParticipants()) {
            log.warn(
                    "Thêm người tham gia thất bại: Vượt quá số lượng tối đa cho phép của sự kiện ID {}",
                    eventId);
            throw new ConflictException(
                    "Số lượng người tham gia vượt quá giới hạn tối đa của sự kiện");
        }

        List<Attendant> attendantsToSave = newParticipants.stream().map(user -> {
            Attendant attendant = new Attendant();
            attendant.setEventId(eventId);
            attendant.setUserId(user.getId());
            return attendant;
        }).collect(Collectors.toList());

        List<Attendant> savedAttendants = attendantRepository.saveAll(attendantsToSave);
        log.info("Đã thêm thành công {} người tham gia mới vào sự kiện ID {}",
                savedAttendants.size(), eventId);

        Map<Integer, User> userMap =
                newParticipants.stream().collect(Collectors.toMap(User::getId, user -> user));

        for (User participant : newParticipants) {
            try {
                emailService.sendEventJoinNotificationEmail(participant, event);
                log.debug("Đã gửi email thông báo tham gia sự kiện cho người dùng '{}'",
                        participant.getEmail());
            } catch (Exception e) {
                log.error("Lỗi khi gửi email thông báo tham gia sự kiện cho người dùng '{}': {}",
                        participant.getEmail(), e.getMessage());
            }
        }

        return savedAttendants.stream().map(attendant -> mapToParticipantResponse(attendant,
                userMap.get(attendant.getUserId()))).collect(Collectors.toList());
    }

    @Override
    public void deleteParticipantsByEventIdAndUsersId(Integer eventId,
            ParticipantsDto participantsDto, String removerEmail) {
        log.info("Người dùng '{}' bắt đầu quá trình xóa {} người tham gia khỏi sự kiện ID {}",
                removerEmail, participantsDto.getEmails().size(), eventId);

        User deleter = userRepository.findByEmail(removerEmail).orElseThrow(
                () -> new AuthException("Người dùng thực hiện hành động không tồn tại."));

        EventManagement deleterRole =
                eventManagerRepository.findByUserIdAndEventId(deleter.getId(), eventId)
                        .map(EventManager::getRoleType).orElse(null);

        List<String> emailsToDelete = participantsDto.getEmails().stream()
                .map(ParticipantDto::getEmail).collect(Collectors.toList());

        if (emailsToDelete.isEmpty()) {
            log.debug("Danh sách email cần xóa trống, không thực hiện hành động nào.");
            return;
        }

        List<Integer> userIdsToDelete = userRepository.findAllByEmailIn(emailsToDelete).stream()
                .map(User::getId).collect(Collectors.toList());

        Map<Integer, EventManagement> targetRolesMap = eventManagerRepository
                .findAllByEventIdAndUserIdIn(eventId, userIdsToDelete).stream()
                .collect(Collectors.toMap(EventManager::getUserId, EventManager::getRoleType));

        List<Integer> finalUserIdsToDelete = userIdsToDelete.stream().filter(targetUserId -> {
            EventManagement targetRole = targetRolesMap.get(targetUserId);
            if (deleterRole == EventManagement.MANAGE) {
                boolean canDelete = (targetRole == null || targetRole == EventManagement.STAFF);
                if (!canDelete) {
                    log.warn("Người dùng '{}' (MANAGE) không có quyền xóa user ID {} (vai trò: {})",
                            removerEmail, targetUserId, targetRole);
                }
                return canDelete;
            }
            if (deleterRole == EventManagement.STAFF) {
                boolean canDelete = (targetRole == null);
                if (!canDelete) {
                    log.warn("Người dùng '{}' (STAFF) không có quyền xóa user ID {} (vai trò: {})",
                            removerEmail, targetUserId, targetRole);
                }
                return canDelete;
            }
            return true;
        }).collect(Collectors.toList());

        if (!finalUserIdsToDelete.isEmpty()) {
            long deletedCount =
                    attendantRepository.deleteByEventIdAndUserIdIn(eventId, finalUserIdsToDelete);
            log.info("Đã xóa thành công {}/{} người tham gia khỏi sự kiện ID {}. Yêu cầu bởi '{}'.",
                    deletedCount, userIdsToDelete.size(), eventId, removerEmail);
        } else {
            log.warn("Không có người tham gia nào được xóa khỏi sự kiện ID {} do không đủ quyền.",
                    eventId);
        }
    }

    @Override
    public void cancelMyRegistration(Integer eventId, String userEmail) {
        log.debug("Bắt đầu xử lý tự hủy đăng ký cho user '{}' tại sự kiện ID {}", userEmail,
                eventId);

        User user = userRepository.findByEmail(userEmail).orElseThrow(
                () -> new NotFoundException("Không tìm thấy người dùng với email: " + userEmail));

        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new NotFoundException("Không tìm thấy sự kiện với ID: " + eventId));

        if (getDisplayStatus(event) != EventStatus.UPCOMING) {
            log.warn("Người dùng '{}' cố gắng hủy đăng ký một sự kiện đã/đang diễn ra (ID: {})",
                    userEmail, eventId);
            throw new ConflictException("Chỉ có thể hủy đăng ký cho các sự kiện sắp diễn ra");
        }

        long deletedCount = attendantRepository.deleteByEventIdAndUserId(eventId, user.getId());

        if (deletedCount == 0) {
            log.warn(
                    "Người dùng '{}' cố gắng hủy đăng ký nhưng không tìm thấy bản ghi tham gia cho sự kiện ID {}",
                    userEmail, eventId);
            throw new NotFoundException("Bạn chưa đăng ký tham gia sự kiện này");
        }

        log.info("Người dùng '{}' đã tự hủy đăng ký thành công khỏi sự kiện '{}' (ID: {})",
                userEmail, event.getTitle(), eventId);
    }

    @Override
    public Map<String, Object> importParticipants(Integer eventId, MultipartFile file,
            String managerEmail) {
        log.info("Người dùng '{}' yêu cầu import người tham gia từ file '{}' cho sự kiện ID {}",
                managerEmail, file.getOriginalFilename(), eventId);

        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new NotFoundException("Không tìm thấy sự kiện với ID: " + eventId));

        if (event.getStartTime().isBefore(Instant.now())) {
            throw new ConflictException(
                    "Sự kiện đã bắt đầu hoặc kết thúc, không thể thêm người tham gia");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Sự kiện đã bị hủy, không thể thêm người tham gia");
        }

        List<String> extractedEmails = fileImportService.extractEmails(file);
        int totalProcessed = extractedEmails.size();

        Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
        List<String> validFormatEmails =
                extractedEmails.stream().filter(email -> emailPattern.matcher(email).matches())
                        .collect(Collectors.toList());
        int invalidFormatCount = totalProcessed - validFormatEmails.size();

        if (validFormatEmails.isEmpty()) {
            return Map.of("message", "Không tìm thấy email hợp lệ nào để xử lý.", "total",
                    totalProcessed, "success", 0, "skipped", totalProcessed);
        }

        List<User> users = userRepository.findAllByEmailIn(validFormatEmails);
        int notFoundInDbCount = validFormatEmails.size() - users.size();

        if (users.isEmpty()) {
            return Map.of("message", "Không có email nào tồn tại trong hệ thống.", "total",
                    totalProcessed, "success", 0, "skipped", totalProcessed);
        }

        List<Integer> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        Set<Integer> existingParticipantIds =
                attendantRepository.findAllByEventIdAndUserIdIn(eventId, userIds).stream()
                        .map(Attendant::getUserId).collect(Collectors.toSet());

        List<User> newParticipants =
                users.stream().filter(user -> !existingParticipantIds.contains(user.getId()))
                        .collect(Collectors.toList());

        int alreadyJoinedCount = users.size() - newParticipants.size();

        int currentParticipantsCount = attendantRepository.countByEventId(eventId);
        if (event.getMaxParticipants() != null
                && currentParticipantsCount + newParticipants.size() > event.getMaxParticipants()) {
            throw new ConflictException(
                    "Số lượng người tham gia (bao gồm import mới) vượt quá giới hạn tối đa của sự kiện");
        }

        List<Attendant> attendantsToSave = newParticipants.stream().map(user -> {
            Attendant attendant = new Attendant();
            attendant.setEventId(eventId);
            attendant.setUserId(user.getId());
            return attendant;
        }).collect(Collectors.toList());

        attendantRepository.saveAll(attendantsToSave);

        for (User participant : newParticipants) {
            try {
                emailService.sendEventJoinNotificationEmail(participant, event);
            } catch (Exception e) {
                log.error("Lỗi khi gửi email cho user {}: {}", participant.getEmail(),
                        e.getMessage());
            }
        }

        int successCount = attendantsToSave.size();
        int skippedCount = invalidFormatCount + notFoundInDbCount + alreadyJoinedCount;

        String message = String.format(
                "Đã gửi mời thành công %d email. Bỏ qua %d email không hợp lệ hoặc đã tồn tại.",
                successCount, skippedCount);

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("total", totalProcessed);
        response.put("success", successCount);
        response.put("skipped", skippedCount);
        response.put("details", Map.of("invalidFormat", invalidFormatCount, "notFoundInDb",
                notFoundInDbCount, "alreadyJoined", alreadyJoinedCount));

        return response;
    }

    @Override
    @Async("importTaskExecutor")
    public void importParticipantsAsync(final Integer eventId, final byte[] fileContent,
            final String fileName, final String managerEmail, final Integer jobId) {
        log.info("Starting async import for job ID: {}, event ID: {}, file: {}", jobId, eventId,
                fileName);
        try {
            importJobService.updateImportJobStatus(jobId, ImportJobStatus.PROCESSING);

            final Event event = eventRepository.findById(eventId).orElseThrow(
                    () -> new NotFoundException("Không tìm thấy sự kiện với ID: " + eventId));

            if (event.getStartTime().isBefore(Instant.now())) {
                throw new ConflictException(
                        "Sự kiện đã bắt đầu hoặc kết thúc, không thể thêm người tham gia");
            }
            if (event.getStatus() == EventStatus.CANCELLED) {
                throw new ConflictException("Sự kiện đã bị hủy, không thể thêm người tham gia");
            }

            // Tạo MultipartFile từ byte array để sử dụng với FileImportService
            final MultipartFile file = new ByteArrayMultipartFile(fileContent, fileName);

            // Step 1: Extract emails (10% progress)
            final List<String> extractedEmails = fileImportService.extractEmails(file);
            final int totalProcessed = extractedEmails.size();
            importJobService.setTotalRecords(jobId, totalProcessed);
            importJobService.updateImportJobProgress(jobId, (int) (totalProcessed * 0.1));

            // Step 2: Validate email format (30% progress)
            final Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
            final List<String> validFormatEmails =
                    extractedEmails.stream().filter(email -> emailPattern.matcher(email).matches())
                            .collect(Collectors.toList());
            final int invalidFormatCount = totalProcessed - validFormatEmails.size();
            importJobService.updateImportJobProgress(jobId, (int) (totalProcessed * 0.3));

            if (validFormatEmails.isEmpty()) {
                final Map<String, Object> details = Map.of("invalidFormat", invalidFormatCount,
                        "notFoundInDb", 0, "alreadyJoined", 0);
                importJobService.updateImportJobResult(jobId, totalProcessed, 0, totalProcessed,
                        objectMapper.writeValueAsString(details));
                importJobService.updateImportJobProgress(jobId, totalProcessed); // 100%
                return;
            }

            // Step 3: Find users in database (50% progress)
            final List<User> users = userRepository.findAllByEmailIn(validFormatEmails);
            final int notFoundInDbCount = validFormatEmails.size() - users.size();
            importJobService.updateImportJobProgress(jobId, (int) (totalProcessed * 0.5)); // 50%
                                                                                           // after
                                                                                           // finding
                                                                                           // users

            if (users.isEmpty()) {
                final Map<String, Object> details = Map.of("invalidFormat", invalidFormatCount,
                        "notFoundInDb", notFoundInDbCount, "alreadyJoined", 0);
                importJobService.updateImportJobResult(jobId, totalProcessed, 0, totalProcessed,
                        objectMapper.writeValueAsString(details));
                importJobService.updateImportJobProgress(jobId, totalProcessed); // 100%
                return;
            }

            // Step 4: Filter existing participants (70% progress)
            final List<Integer> userIds =
                    users.stream().map(User::getId).collect(Collectors.toList());
            final Set<Integer> existingParticipantIds =
                    attendantRepository.findAllByEventIdAndUserIdIn(eventId, userIds).stream()
                            .map(Attendant::getUserId).collect(Collectors.toSet());

            final List<User> newParticipants =
                    users.stream().filter(user -> !existingParticipantIds.contains(user.getId()))
                            .collect(Collectors.toList());

            final int alreadyJoinedCount = users.size() - newParticipants.size();
            importJobService.updateImportJobProgress(jobId, (int) (totalProcessed * 0.7)); // 70%
                                                                                           // after
                                                                                           // filtering

            final int currentParticipantsCount = attendantRepository.countByEventId(eventId);
            if (event.getMaxParticipants() != null && currentParticipantsCount
                    + newParticipants.size() > event.getMaxParticipants()) {
                throw new ConflictException(
                        "Số lượng người tham gia (bao gồm import mới) vượt quá giới hạn tối đa của sự kiện");
            }

            // Step 5: Save attendants (90% progress)
            final List<Attendant> attendantsToSave = newParticipants.stream().map(user -> {
                final Attendant attendant = new Attendant();
                attendant.setEventId(eventId);
                attendant.setUserId(user.getId());
                return attendant;
            }).collect(Collectors.toList());

            attendantRepository.saveAll(attendantsToSave);
            importJobService.updateImportJobProgress(jobId, (int) (totalProcessed * 0.9)); // 90%
                                                                                           // after
                                                                                           // saving

            // Step 6: Send emails (100% progress)
            int emailSentCount = 0;
            for (final User participant : newParticipants) {
                try {
                    emailService.sendEventJoinNotificationEmail(participant, event);
                    emailSentCount++;
                    // Update progress for each email sent
                    if (emailSentCount % 10 == 0 || emailSentCount == newParticipants.size()) {
                        final int progress = (int) (totalProcessed * 0.9
                                + (emailSentCount * totalProcessed * 0.1) / newParticipants.size());
                        importJobService.updateImportJobProgress(jobId, progress);
                    }
                } catch (final Exception e) {
                    log.error("Lỗi khi gửi email cho user {}: {}", participant.getEmail(),
                            e.getMessage());
                }
            }

            final int successCount = attendantsToSave.size();
            final int skippedCount = invalidFormatCount + notFoundInDbCount + alreadyJoinedCount;

            final Map<String, Object> details = Map.of("invalidFormat", invalidFormatCount,
                    "notFoundInDb", notFoundInDbCount, "alreadyJoined", alreadyJoinedCount);

            importJobService.updateImportJobResult(jobId, totalProcessed, successCount,
                    skippedCount, objectMapper.writeValueAsString(details));

            log.info("Completed async import for job ID: {}, success: {}, skipped: {}", jobId,
                    successCount, skippedCount);
        } catch (final Exception e) {
            log.error("Error processing async import for job ID: {}", jobId, e);
            importJobService.updateImportJobError(jobId, e.getMessage());
        }
    }

    private ParticipantResponse mapToParticipantResponse(Attendant attendant, User user) {
        UserResponseDTO userResponse = UserResponseDTO.builder().id(user.getId())
                .email(user.getEmail()).name(user.getName()).phoneNumber(user.getPhoneNumber())
                .enabled(user.getEnabled()).roles(user.getRoles()).build();

        return ParticipantResponse.builder().id(attendant.getId()).eventId(attendant.getEventId())
                .joinedAt(attendant.getJoinedAt()).checkInTime(attendant.getCheckedTime())
                .user(userResponse).build();
    }

    private ParticipantResponse mapToParticipantResponse(Attendant attendant, User user,
            Unit unit) {
        UnitResponseDTO unitResponse = null;
        if (unit != null) {
            unitResponse = UnitResponseDTO.builder().id(unit.getId()).unitName(unit.getUnitName())
                    .unitType(unit.getUnitType()).parentId(unit.getParentId()).build();
        }

        UserResponseDTO userResponse = UserResponseDTO.builder().id(user.getId())
                .email(user.getEmail()).name(user.getName()).phoneNumber(user.getPhoneNumber())
                .enabled(user.getEnabled()).roles(user.getRoles()).unit(unitResponse).build();

        return ParticipantResponse.builder().id(attendant.getId()).eventId(attendant.getEventId())
                .joinedAt(attendant.getJoinedAt()).checkInTime(attendant.getCheckedTime())
                .user(userResponse).build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportParticipantsToExcel(final Integer eventId, final String filter)
            throws Exception {
        log.info("Bắt đầu xuất danh sách người tham dự ra Excel cho sự kiện ID: {}, filter: {}",
                eventId, filter);

        // Get event to retrieve title
        final Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new NotFoundException("Không tìm thấy sự kiện với ID: " + eventId));

        // Get participants based on filter
        final List<ParticipantResponse> participants;
        if ("checked-in".equalsIgnoreCase(filter)) {
            // Get only checked-in participants
            final List<Attendant> checkedInAttendants = attendantRepository
                    .findByEventIdAndCheckedTimeIsNotNullOrderByCheckedTimeAsc(eventId);
            participants = mapAttendantsToParticipantResponses(checkedInAttendants);
        } else {
            // Get all participants (default)
            participants = getParticipantByEventId(eventId);
        }

        if (participants.isEmpty()) {
            log.warn("Không có người tham dự nào để xuất cho sự kiện ID: {}", eventId);
            throw new NotFoundException("Không có người tham dự nào để xuất");
        }

        // Export to Excel
        final byte[] excelBytes =
                fileExportService.exportParticipantsToExcel(participants, event.getTitle());

        log.info("Đã xuất thành công {} người tham dự ra Excel cho sự kiện ID: {}",
                participants.size(), eventId);
        return excelBytes;
    }

    /**
     * Maps a list of Attendants to ParticipantResponse list. Follows DRY principle by reusing
     * mapping logic.
     */
    private List<ParticipantResponse> mapAttendantsToParticipantResponses(
            final List<Attendant> attendants) {
        if (attendants.isEmpty()) {
            return Collections.emptyList();
        }

        final Set<Integer> userIds =
                attendants.stream().map(Attendant::getUserId).collect(Collectors.toSet());

        final Map<Integer, User> userMap = userRepository.findAllById(new ArrayList<>(userIds))
                .stream().collect(Collectors.toMap(User::getId, user -> user));

        final Set<Integer> unitIds = userMap.values().stream().map(User::getUnitId)
                .filter(id -> id != null).collect(Collectors.toSet());

        final Map<Integer, Unit> unitMap = unitIds.isEmpty() ? Collections.emptyMap()
                : unitRepository.findAllById(new ArrayList<>(unitIds)).stream()
                        .collect(Collectors.toMap(Unit::getId, unit -> unit));

        final Map<Integer, Unit> finalUnitMap = unitMap;

        return attendants.stream().map(attendant -> {
            final User user = userMap.get(attendant.getUserId());
            if (user == null) {
                return null;
            }
            final Unit unit =
                    (user.getUnitId() != null) ? finalUnitMap.get(user.getUnitId()) : null;
            return mapToParticipantResponse(attendant, user, unit);
        }).filter(response -> response != null).collect(Collectors.toList());
    }

    private EventStatus getDisplayStatus(Event event) {
        Instant now = Instant.now();
        if (event.getStatus() == EventStatus.CANCELLED)
            return EventStatus.CANCELLED;
        if (event.getStatus() == EventStatus.COMPLETED)
            return EventStatus.COMPLETED;
        if (now.isBefore(event.getStartTime()))
            return EventStatus.UPCOMING;
        if (now.isAfter(event.getEndTime()))
            return EventStatus.COMPLETED;
        return EventStatus.ONGOING;
    }
}
