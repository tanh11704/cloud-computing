package API_BoPhieu.service.file;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import API_BoPhieu.exception.FileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final Cloudinary cloudinary;

    @Override
    public String storeFile(MultipartFile file, String subDir, String baseName) {
        try {
            // Loại bỏ extension từ baseName nếu có (để tránh trùng lặp extension)
            String cleanBaseName = removeExtension(baseName);
            String uniqueName = cleanBaseName + "_" + System.currentTimeMillis();
            // Tạo publicId với folder path, Cloudinary sẽ tự động thêm extension từ file
            String publicId = String.format("%s/%s", subDir, uniqueName);

            Map<String, Object> uploadParams = new HashMap<>();
            uploadParams.put("public_id", publicId);
            // Không cần thêm folder parameter vì đã có trong public_id
            uploadParams.put("resource_type", "auto");

            cloudinary.uploader().upload(file.getBytes(), uploadParams);

            log.info("File uploaded successfully to Cloudinary: {}", publicId);
            return publicId;

        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary", e);
            throw new FileException("Không thể lưu trữ tệp. Vui lòng thử lại!");
        } catch (Exception e) {
            log.error("Error uploading file to Cloudinary", e);
            throw new FileException("Không thể lưu trữ tệp. Vui lòng thử lại!");
        }
    }

    @Override
    public Resource downloadPrivateFile(String publicId) {
        try {
            String url = cloudinary.url().publicId(publicId).generate();
            java.net.URL fileUrl = new java.net.URL(url);
            java.io.InputStream stream = fileUrl.openStream();
            return new InputStreamResource(stream);
        } catch (Exception e) {
            log.warn("Failed to load file from Cloudinary: {}", publicId, e);
            throw new FileException("Không tìm thấy tệp: " + publicId);
        }
    }

    @Override
    public void deleteFile(String publicId) {
        try {
            String cleanPublicId = publicId.startsWith("/") ? publicId.substring(1) : publicId;
            Map<?, ?> result = cloudinary.uploader().destroy(cleanPublicId, ObjectUtils.emptyMap());
            String resultStatus = (String) result.get("result");

            if ("ok".equals(resultStatus)) {
                log.info("File deleted successfully from Cloudinary: {}", publicId);
            } else {
                log.warn("File deletion may have failed from Cloudinary: {}, result: {}", publicId,
                        resultStatus);
            }
        } catch (Exception e) {
            log.error("Error deleting file from Cloudinary: {}", publicId, e);
            throw new FileException("Không thể xóa tệp: " + e.getMessage());
        }
    }

    private String removeExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return filename;
        }
        return filename.substring(0, filename.lastIndexOf("."));
    }
}
