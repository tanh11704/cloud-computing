package API_BoPhieu.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateDescriptionResponse {
    private String description; // Nội dung mô tả đã được tạo (có thể chứa HTML cơ bản)
    private String rawText; // Nội dung text thuần (không có HTML)
}

