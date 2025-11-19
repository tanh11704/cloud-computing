package API_BoPhieu.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import API_BoPhieu.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("${api.prefix}/medias")
@RequiredArgsConstructor
@Slf4j
public class MediaController {
    private final FileStorageService fileStorageService;

    @PostMapping("/image-upload")
    public ResponseEntity<?> uploadImage(@RequestParam("image") MultipartFile file) {
        log.info("Nhận yêu cầu upload ảnh: {}, kích thước: {} bytes", file.getOriginalFilename(),
                file.getSize());

        if (file.getSize() > 10 * 1024 * 1024) { // 10MB
            log.warn("File upload có kích thước quá lớn: {} bytes", file.getSize());
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Kích thước file không được quá 10MB"));
        }

        String objectKey = fileStorageService.storeFile(file, "images", "img_");

        log.info("Upload ảnh thành công. Key: {}", objectKey);

        return ResponseEntity.ok(Map.of("url", objectKey));
    }
}
