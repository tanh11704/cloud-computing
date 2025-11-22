package API_BoPhieu.service.file;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FileImportService {
    List<String> extractEmails(MultipartFile file);
}

