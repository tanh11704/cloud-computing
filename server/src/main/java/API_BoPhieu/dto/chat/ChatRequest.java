package API_BoPhieu.dto.chat;

import lombok.Data;

@Data
public class ChatRequest {
    private Integer eventId;
    private String message;
}

