package API_BoPhieu.dto.chat;

import java.time.Instant;
import API_BoPhieu.constants.ChatSender;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageDto {
    private Integer id;
    private ChatSender sender;
    private String content;
    private Instant timestamp;
}

