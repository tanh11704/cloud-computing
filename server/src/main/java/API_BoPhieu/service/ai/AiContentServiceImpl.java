package API_BoPhieu.service.ai;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import API_BoPhieu.dto.ai.GenerateDescriptionRequest;
import API_BoPhieu.dto.ai.GenerateDescriptionResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AiContentServiceImpl implements AiContentService {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi"))
                    .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;

    public AiContentServiceImpl() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public GenerateDescriptionResponse generateEventDescription(GenerateDescriptionRequest request)
            throws Exception {
        log.info("Bắt đầu tạo mô tả sự kiện bằng AI cho sự kiện: {}", request.getTitle());

        try {
            String prompt = buildPrompt(request);
            String generatedContent = callGeminiApi(prompt);

            // Xử lý kết quả: tách raw text và HTML format
            String rawText = cleanText(generatedContent);
            String htmlContent = formatAsHtml(rawText);

            log.info("Tạo mô tả thành công cho sự kiện: {}", request.getTitle());
            return GenerateDescriptionResponse.builder().description(htmlContent).rawText(rawText)
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("Lỗi client khi gọi Gemini API: {}", e.getMessage());
            if (e.getStatusCode().value() == 429) {
                throw new Exception("AI đang quá tải, vui lòng thử lại sau giây lát");
            } else if (e.getStatusCode().value() == 400) {
                throw new Exception("Yêu cầu không hợp lệ, vui lòng kiểm tra lại thông tin");
            }
            throw new Exception("Lỗi khi gọi AI: " + e.getMessage());
        } catch (HttpServerErrorException e) {
            log.error("Lỗi server khi gọi Gemini API: {}", e.getMessage());
            throw new Exception("AI đang bận, vui lòng thử lại sau giây lát");
        } catch (RestClientException e) {
            log.error("Lỗi kết nối đến Gemini API: {}", e.getMessage());
            throw new Exception("Không thể kết nối đến AI, vui lòng kiểm tra kết nối mạng");
        } catch (Exception e) {
            log.error("Lỗi không xác định khi tạo mô tả: {}", e.getMessage(), e);
            throw new Exception("Đã xảy ra lỗi khi tạo mô tả: " + e.getMessage());
        }
    }

    private String buildPrompt(GenerateDescriptionRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Bạn là một chuyên gia viết nội dung quảng bá sự kiện. ");
        prompt.append("Hãy viết một đoạn mô tả hấp dẫn cho sự kiện sau đây:\n\n");

        // Thông tin cơ bản
        prompt.append("Tên sự kiện: ").append(request.getTitle()).append("\n");

        if (request.getLocation() != null && !request.getLocation().trim().isEmpty()) {
            prompt.append("Địa điểm: ").append(request.getLocation()).append("\n");
        }

        if (request.getStartTime() != null) {
            String startTimeStr = DATE_TIME_FORMATTER.format(request.getStartTime());
            prompt.append("Thời gian bắt đầu: ").append(startTimeStr).append("\n");
        }

        if (request.getEndTime() != null) {
            String endTimeStr = DATE_TIME_FORMATTER.format(request.getEndTime());
            prompt.append("Thời gian kết thúc: ").append(endTimeStr).append("\n");
        }

        if (request.getSpeakers() != null && !request.getSpeakers().isEmpty()) {
            prompt.append("Diễn giả: ").append(String.join(", ", request.getSpeakers()))
                    .append("\n");
        }

        if (request.getAdditionalInfo() != null && !request.getAdditionalInfo().trim().isEmpty()) {
            prompt.append("Thông tin bổ sung: ").append(request.getAdditionalInfo()).append("\n");
        }

        prompt.append("\n");

        // Yêu cầu về giọng văn
        prompt.append("Yêu cầu:\n");
        prompt.append("- Giọng văn: ");
        switch (request.getTone()) {
            case PROFESSIONAL:
                prompt.append("Chuyên nghiệp, trang trọng\n");
                break;
            case FRIENDLY:
                prompt.append("Thân thiện, gần gũi\n");
                break;
            case EXCITING:
                prompt.append("Hào hứng, nhiệt tình\n");
                break;
            case FORMAL:
                prompt.append("Trang trọng, lịch sự\n");
                break;
            case CASUAL:
                prompt.append("Thân mật, tự nhiên\n");
                break;
        }

        // Yêu cầu về độ dài
        prompt.append("- Độ dài: ");
        switch (request.getLength()) {
            case SHORT:
                prompt.append("Ngắn gọn (50-100 từ)\n");
                break;
            case MEDIUM:
                prompt.append("Trung bình (100-200 từ)\n");
                break;
            case LONG:
                prompt.append("Chi tiết (200-300 từ)\n");
                break;
        }

        // Yêu cầu về nền tảng
        prompt.append("- Nền tảng: ");
        switch (request.getTarget()) {
            case WEBSITE:
                prompt.append("Website (có thể dùng HTML cơ bản như <br>, <strong>)\n");
                break;
            case FACEBOOK:
                prompt.append("Facebook (ngắn gọn, hấp dẫn, có thể dùng emoji)\n");
                break;
            case EMAIL:
                prompt.append("Email marketing (chuyên nghiệp, có call-to-action)\n");
                break;
            case GENERAL:
                prompt.append("Tổng quát (phù hợp nhiều nền tảng)\n");
                break;
        }

        prompt.append("\n");
        prompt.append("Hãy viết mô tả bằng tiếng Việt, hấp dẫn và thu hút người đọc. ");
        prompt.append("Không cần thêm tiêu đề, chỉ cần nội dung mô tả.");

        return prompt.toString();
    }

    private String callGeminiApi(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        parts.add(part);
        content.put("parts", parts);
        contents.add(content);
        requestBody.put("contents", contents);

        // Cấu hình generation config
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 1024);
        requestBody.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = GEMINI_API_URL + geminiApiKey;

        log.debug("Gọi Gemini API với prompt length: {}", prompt.length());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ResponseEntity<Map> response =
                restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

        // Parse response
        if (response.getBody() != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) response.getBody().get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                if (contentMap != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> responseParts =
                            (List<Map<String, Object>>) contentMap.get("parts");
                    if (responseParts != null && !responseParts.isEmpty()) {
                        String text = (String) responseParts.get(0).get("text");
                        return text != null ? text.trim() : "";
                    }
                }
            }
        }

        throw new Exception("Không nhận được phản hồi hợp lệ từ Gemini API");
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        // Loại bỏ các ký tự đặc biệt không mong muốn
        return text.trim().replaceAll("\\*\\*", "").replaceAll("\\*", "");
    }

    private String formatAsHtml(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        // Chuyển đổi xuống dòng thành <br>
        String html = text.replaceAll("\n\n+", "</p><p>").replaceAll("\n", "<br>");
        // Wrap trong paragraph tags nếu chưa có
        if (!html.startsWith("<p>")) {
            html = "<p>" + html + "</p>";
        }
        return html;
    }
}

