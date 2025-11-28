package API_BoPhieu.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import API_BoPhieu.constants.EventStatus;
import API_BoPhieu.dto.ai.GenerateDescriptionRequest;
import API_BoPhieu.dto.ai.GenerateDescriptionResponse;
import API_BoPhieu.dto.attendant.ParticipantResponse;
import API_BoPhieu.dto.attendant.ParticipantsDto;
import API_BoPhieu.dto.event.EventDetailResponse;
import API_BoPhieu.dto.event.EventDto;
import API_BoPhieu.dto.event.EventPageWithCountersResponse;
import API_BoPhieu.dto.event.EventResponse;
import API_BoPhieu.entity.Attendant;
import API_BoPhieu.exception.AuthException;
import API_BoPhieu.service.ai.AiContentService;
import API_BoPhieu.service.attendant.AttendantService;
import API_BoPhieu.service.event.EventService;
import API_BoPhieu.service.sse.event_list.EventListSseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("${api.prefix}/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {
    private final EventService eventService;
    private final AttendantService attendantService;
    private final EventListSseService eventListSseService;
    private final AiContentService aiContentService;

    @PutMapping("/{id}/upload-banner")
    public ResponseEntity<?> uploadBanner(@PathVariable("id") Integer eventId,
            @RequestParam("banner") MultipartFile bannerFile) {
        EventResponse eventResponse = eventService.uploadBanner(eventId, bannerFile);
        return ResponseEntity.ok().body(eventResponse);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@RequestBody EventDto eventDto,
            Authentication authentication) {
        EventResponse eventResponse = eventService.createEvent(eventDto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(eventResponse);
    }

    @PostMapping("/join/{eventToken}")
    public ResponseEntity<Attendant> joinEvent(@PathVariable String eventToken,
            Authentication authentication) {
        Attendant newAttendant = eventService.joinEvent(eventToken, authentication.getName());
        return ResponseEntity.ok(newAttendant);
    }

    @PostMapping("/{eventId}/participants")
    public ResponseEntity<List<ParticipantResponse>> addParticipants(@PathVariable Integer eventId,
            @RequestBody ParticipantsDto participantsDto, Authentication authentication) {
        List<ParticipantResponse> response = attendantService.addParticipants(eventId,
                participantsDto, authentication.getName());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping(("/{eventId}/participants"))
    public ResponseEntity<?> deleteParticipants(@PathVariable Integer eventId,
            @RequestBody ParticipantsDto participantsDto, Authentication authentication) {
        String removerEmail = authentication.getName();

        attendantService.deleteParticipantsByEventIdAndUsersId(eventId, participantsDto,
                removerEmail);

        return ResponseEntity.ok(Map.of("message", "Xóa người tham gia thành công!"));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable Integer eventId,
            @RequestBody EventDto eventDto) {
        EventResponse eventResponse = eventService.updateEvent(eventId, eventDto);
        return ResponseEntity.ok(eventResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> getEventById(@PathVariable Integer id,
            Authentication authentication) {
        EventDetailResponse eventDetailResponse =
                eventService.getEventById(id, authentication.getName());

        return ResponseEntity.ok(eventDetailResponse);
    }

    @GetMapping
    public ResponseEntity<EventPageWithCountersResponse> getAllEvents(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "startTime") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String search, Authentication authentication) {
        log.debug(
                "API getAllEvents được gọi với các tham số: page={}, size={}, status='{}', search='{}'",
                page, size, status, search);
        String email = authentication != null ? authentication.getName() : null;

        EventPageWithCountersResponse result =
                eventService.getAllEvents(page, size, sortBy, sortDir, status, search, email);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/subscribe")
    public SseEmitter subscribeToEvents() {

        final SseEmitter emitter = new SseEmitter(1800000L);

        eventListSseService.addEmitter(emitter);

        try {
            emitter.send(SseEmitter.event().name("connection_established").data("Connected"));
        } catch (IOException e) {
            log.warn("Không thể gửi sự kiện connection_established: {}", e.getMessage());
        }

        return emitter;
    }

    @GetMapping("/managed")
    public ResponseEntity<EventPageWithCountersResponse> getManagedEvents(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "startTime") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String search, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Người dùng chưa xác thực");
        }

        String email = authentication.getName();
        if (email == null || email.trim().isEmpty()) {
            throw new AuthException("Không thể xác định email người dùng");
        }

        EventPageWithCountersResponse result =
                eventService.getManagedEvents(page, size, sortBy, sortDir, status, search, email);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or @eventAuth.hasEventRole(authentication, #id, T(API_BoPhieu.constants.EventManagement).MANAGE)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        eventService.cancelEvent(id);
    }

    @PostMapping("/generate-description")
    public ResponseEntity<?> generateDescription(
            @Valid @RequestBody GenerateDescriptionRequest request) {
        try {
            log.info("Nhận yêu cầu tạo mô tả sự kiện bằng AI cho: {}", request.getTitle());
            GenerateDescriptionResponse response =
                    aiContentService.generateEventDescription(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Lỗi khi tạo mô tả sự kiện bằng AI: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
