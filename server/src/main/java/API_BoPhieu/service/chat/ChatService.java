package API_BoPhieu.service.chat;

import java.util.List;
import API_BoPhieu.dto.chat.ChatRequest;
import API_BoPhieu.dto.chat.ChatResponse;
import API_BoPhieu.dto.chat.MessageDto;

public interface ChatService {
    ChatResponse processMessage(Integer userId, ChatRequest request);

    List<MessageDto> getChatHistory(Integer userId, Integer eventId);
}

