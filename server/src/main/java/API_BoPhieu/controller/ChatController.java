package API_BoPhieu.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import API_BoPhieu.dto.chat.ChatRequest;
import API_BoPhieu.dto.chat.ChatResponse;
import API_BoPhieu.dto.chat.MessageDto;
import API_BoPhieu.entity.User;
import API_BoPhieu.repository.UserRepository;
import API_BoPhieu.service.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Tag(name = "Chatbot", description = "Event Assistant Chatbot API")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    @PostMapping("/send")
    @Operation(summary = "Send message to chatbot",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ChatResponse> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails, @RequestBody ChatRequest request) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("không tìm thấy người dùng"));

        return ResponseEntity.ok(chatService.processMessage(user.getId(), request));
    }

    @GetMapping("/history/{eventId}")
    @Operation(summary = "Get chat history for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<MessageDto>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Integer eventId) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("không tìm thấy người dùng"));

        return ResponseEntity.ok(chatService.getChatHistory(user.getId(), eventId));
    }
}

