package API_BoPhieu.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import API_BoPhieu.dto.attendant.ParticipantResponse;
import API_BoPhieu.dto.import_job.ImportJobResponse;
import API_BoPhieu.entity.Attendant;
import API_BoPhieu.entity.ImportJob;
import API_BoPhieu.service.attendant.AttendantService;
import API_BoPhieu.service.import_job.ImportJobService;
import API_BoPhieu.service.sse.check_in.CheckInSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("${api.prefix}/attendants")
@RequiredArgsConstructor
@Slf4j
public class AttendantController {

    private final AttendantService attendantService;
    private final CheckInSseService sseService;
    private final ImportJobService importJobService;

    @GetMapping("/subscribe/{eventId}")
    public SseEmitter subscribeToEvents(@PathVariable Integer eventId) {
        log.info("Client mới yêu cầu kết nối SSE tới sự kiện ID: {}", eventId);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        sseService.addEmitter(eventId, emitter);

        try {
            emitter.send(SseEmitter.event().name("INIT")
                    .data("Kết nối SSE thành công tới sự kiện " + eventId));
        } catch (IOException e) {
            log.warn("Không thể gửi sự kiện INIT tới client cho sự kiện ID {}: {}", eventId,
                    e.getMessage());
        }

        log.debug("Đã trả về emitter cho client của sự kiện ID {}", eventId);
        return emitter;
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).STAFF) or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).MANAGE)")
    public ResponseEntity<List<ParticipantResponse>> getParticipantsByEventId(
            @PathVariable Integer eventId) {
        log.debug("Nhận yêu cầu lấy danh sách người tham gia cho sự kiện ID: {}", eventId);
        List<ParticipantResponse> responses = attendantService.getParticipantByEventId(eventId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping(value = "/{eventId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_ADMIN') or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).STAFF) or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).MANAGE)")
    public ResponseEntity<Map<String, Object>> importParticipants(@PathVariable Integer eventId,
            @RequestParam("file") MultipartFile file, Authentication authentication) {
        log.info("Nhận yêu cầu import người tham gia từ file cho sự kiện ID: {}", eventId);

        final ImportJob job = importJobService.createImportJob(eventId, authentication.getName(),
                file.getOriginalFilename());

        // Đọc file content vào memory trước khi async để tránh file bị xóa
        final byte[] fileContent;
        try {
            fileContent = file.getBytes();
        } catch (final Exception e) {
            log.error("Lỗi khi đọc file content: ", e);
            importJobService.updateImportJobError(job.getId(),
                    "Không thể đọc file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Không thể đọc file", "jobId", job.getId()));
        }

        attendantService.importParticipantsAsync(eventId, fileContent, file.getOriginalFilename(),
                authentication.getName(), job.getId());

        final Map<String, Object> response = Map.of("job_id", job.getId(), "status", "ACCEPTED",
                "message",
                "Import đã được chấp nhận và đang xử lý. Vui lòng kiểm tra trạng thái qua endpoint /attendants/import/{jobId}");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/import/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ImportJobResponse> getImportJobStatus(@PathVariable Integer jobId,
            Authentication authentication) {
        log.debug("Nhận yêu cầu lấy trạng thái import job ID: {}", jobId);
        final ImportJob job = importJobService.getImportJobById(jobId);

        if (!job.getCreatedBy().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        final ImportJobResponse response = ImportJobResponse.builder().id(job.getId())
                .eventId(job.getEventId()).createdBy(job.getCreatedBy()).fileName(job.getFileName())
                .status(job.getStatus()).totalRecords(job.getTotalRecords())
                .processedCount(job.getProcessedCount()).successCount(job.getSuccessCount())
                .skippedCount(job.getSkippedCount()).errorMessage(job.getErrorMessage())
                .resultDetails(job.getResultDetails()).createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt()).build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-in/{eventToken}")
    public ResponseEntity<Attendant> checkInEvent(@PathVariable String eventToken,
            Authentication authentication) {
        log.info("Nhận yêu cầu check-in từ người dùng '{}' cho sự kiện với token '{}'",
                authentication.getName(), eventToken);
        Attendant attendant = attendantService.checkIn(eventToken, authentication.getName());
        return ResponseEntity.ok(attendant);
    }

    @GetMapping("/get-qr-check/{eventId}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).STAFF) or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).MANAGE)")
    public ResponseEntity<byte[]> getQrCheck(@PathVariable Integer eventId) throws Exception {
        log.debug("Nhận yêu cầu tạo QR check-in cho sự kiện ID: {}", eventId);
        byte[] qrCodeImage = attendantService.generateQrCheck(eventId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(qrCodeImage.length);
        return new ResponseEntity<>(qrCodeImage, headers, HttpStatus.OK);
    }

    @DeleteMapping("/{eventId}/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).MANAGE)")
    public ResponseEntity<Map<String, String>> deleteParticipant(@PathVariable Integer eventId,
            @PathVariable Integer userId, Authentication authentication) {
        log.info("Người dùng '{}' yêu cầu xóa user ID {} khỏi sự kiện ID {}",
                authentication.getName(), userId, eventId);
        attendantService.deleteParticipantByEventIdAndUserId(eventId, userId);
        return ResponseEntity.ok(Map.of("message", "Xóa người tham gia thành công"));
    }

    @DeleteMapping("/my-registration/{eventId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> cancelMyRegistration(@PathVariable Integer eventId,
            Authentication authentication) {

        log.info("Người dùng '{}' yêu cầu tự hủy đăng ký khỏi sự kiện ID {}",
                authentication.getName(), eventId);
        attendantService.cancelMyRegistration(eventId, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Hủy đăng ký thành công."));
    }

    @GetMapping("/{eventId}/export")
    @PreAuthorize("hasRole('ROLE_ADMIN') or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).STAFF) or @eventAuth.hasEventRole(authentication, #eventId, T(API_BoPhieu.constants.EventManagement).MANAGE)")
    public ResponseEntity<byte[]> exportParticipantsToExcel(@PathVariable Integer eventId,
            @RequestParam(value = "filter", defaultValue = "all") String filter) throws Exception {
        log.info(
                "Nhận yêu cầu xuất danh sách người tham dự ra Excel cho sự kiện ID: {}, filter: {}",
                eventId, filter);

        final byte[] excelBytes = attendantService.exportParticipantsToExcel(eventId, filter);

        // Generate filename with event ID and timestamp
        final String filename = String.format("Danh_sach_nguoi_tham_du_%d_%d.xlsx", eventId,
                System.currentTimeMillis());

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(excelBytes.length);

        log.info("Đã xuất thành công file Excel cho sự kiện ID: {}, kích thước: {} bytes", eventId,
                excelBytes.length);
        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }
}
