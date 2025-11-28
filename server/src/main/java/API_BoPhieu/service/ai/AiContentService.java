package API_BoPhieu.service.ai;

import API_BoPhieu.dto.ai.GenerateDescriptionRequest;
import API_BoPhieu.dto.ai.GenerateDescriptionResponse;

public interface AiContentService {
    /**
     * Tạo mô tả sự kiện bằng AI (Gemini)
     *
     * @param request Thông tin sự kiện và cấu hình AI
     * @return Nội dung mô tả đã được tạo
     * @throws Exception Nếu có lỗi khi gọi Gemini API
     */
    GenerateDescriptionResponse generateEventDescription(GenerateDescriptionRequest request)
            throws Exception;
}

