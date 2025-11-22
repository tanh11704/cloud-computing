package API_BoPhieu.service.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import API_BoPhieu.constants.ChatSender;
import API_BoPhieu.dto.chat.ChatRequest;
import API_BoPhieu.dto.chat.ChatResponse;
import API_BoPhieu.dto.chat.MessageDto;
import API_BoPhieu.entity.ChatMessage;
import API_BoPhieu.entity.ChatSession;
import API_BoPhieu.entity.Event;
import API_BoPhieu.exception.ResourceNotFoundException;
import API_BoPhieu.repository.ChatMessageRepository;
import API_BoPhieu.repository.ChatSessionRepository;
import API_BoPhieu.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EventRepository eventRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=";

    @Override
    @Transactional
    public ChatResponse processMessage(Integer userId, ChatRequest request) {
        // 1. Find or Create Session
        ChatSession session = chatSessionRepository
                .findByUserIdAndEventId(userId, request.getEventId()).orElseGet(() -> {
                    ChatSession newSession = new ChatSession();
                    newSession.setUserId(userId);
                    newSession.setEventId(request.getEventId());
                    return chatSessionRepository.save(newSession);
                });

        // 2. Save User Message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setSender(ChatSender.USER);
        userMsg.setContent(request.getMessage());
        chatMessageRepository.save(userMsg);

        // 3. Fetch Event Context
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        // 4. Call Gemini API
        // Fetch history to provide context
        List<ChatMessage> history =
                chatMessageRepository.findBySessionIdOrderByTimestampAsc(session.getId());

        String botResponseText = callGemini(event, request.getMessage(), history);

        // 5. Save Bot Response
        ChatMessage botMsg = new ChatMessage();
        botMsg.setSessionId(session.getId());
        botMsg.setSender(ChatSender.BOT);
        botMsg.setContent(botResponseText);
        chatMessageRepository.save(botMsg);

        return new ChatResponse(botResponseText);
    }

    @Override
    public List<MessageDto> getChatHistory(Integer userId, Integer eventId) {
        ChatSession session =
                chatSessionRepository.findByUserIdAndEventId(userId, eventId).orElse(null);

        if (session == null) {
            return Collections.emptyList();
        }

        return chatMessageRepository.findBySessionIdOrderByTimestampAsc(session.getId()).stream()
                .map(msg -> MessageDto.builder().id(msg.getId()).sender(msg.getSender())
                        .content(msg.getContent()).timestamp(msg.getTimestamp()).build())
                .collect(Collectors.toList());
    }

    private String callGemini(Event event, String userMessage, List<ChatMessage> history) {
        try {
            // Construct context prompt
            StringBuilder context = new StringBuilder();
            context.append("Bạn là trợ lý ảo hữu ích cho sự kiện: ").append(event.getTitle())
                    .append(".\n");
            context.append("Mô tả: ").append(event.getDescription()).append("\n");
            context.append("Địa điểm: ").append(event.getLocation()).append("\n");
            context.append("Thời gian bắt đầu: ").append(event.getStartTime()).append("\n");
            context.append("Thời gian kết thúc: ").append(event.getEndTime()).append("\n");
            if (event.getUrlDocs() != null)
                context.append("Tài liệu: ").append(event.getUrlDocs()).append("\n");

            context.append("\nLịch sử trò chuyện:\n");
            // Limit history to last 10 messages to avoid token limits
            int start = Math.max(0, history.size() - 11);

            for (int i = start; i < history.size() - 1; i++) {
                ChatMessage msg = history.get(i);
                context.append(msg.getSender() == ChatSender.USER ? "User: " : "Bot: ")
                        .append(msg.getContent()).append("\n");
            }

            context.append("\nUser hỏi: ").append(userMessage).append("\n");
            context.append(
                    "Trả lời chính xác dựa trên thông tin sự kiện đã cung cấp. Nếu không biết câu trả lời, hãy lịch sự khuyên họ liên hệ với ban tổ chức. Trả lời bằng tiếng Việt.");

            // Prepare JSON payload
            Map<String, Object> part = Map.of("text", context.toString());
            Map<String, Object> content = Map.of("parts", List.of(part));
            Map<String, Object> payload = Map.of("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(GEMINI_URL + geminiApiKey, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                // Response structure: candidates[0].content.parts[0].text
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    JsonNode firstCandidate = candidates.get(0);
                    JsonNode parts = firstCandidate.path("content").path("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        return parts.get(0).path("text").asText();
                    }
                }
            }
            return "Xin lỗi, tôi không thể xử lý yêu cầu lúc này.";
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            return "Xin lỗi, tôi đang gặp sự cố kỹ thuật. Vui lòng liên hệ ban tổ chức.";
        }
    }
}
