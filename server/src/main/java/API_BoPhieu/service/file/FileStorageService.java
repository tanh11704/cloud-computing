package API_BoPhieu.service.file;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeFile(MultipartFile file, String subDir, String baseName);

    Resource downloadPrivateFile(String objectKey);

    void deleteFile(String objectKey);
}
