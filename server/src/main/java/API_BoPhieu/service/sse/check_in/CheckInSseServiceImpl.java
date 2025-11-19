package API_BoPhieu.service.sse.check_in;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CheckInSseServiceImpl implements CheckInSseService {
    private final Map<Integer, List<SseEmitter>> emittersByChannelId = new ConcurrentHashMap<>();

    @Override
    public void addEmitter(final Integer channelId, final SseEmitter emitter) {
        final List<SseEmitter> emitters = this.emittersByChannelId.computeIfAbsent(channelId,
                k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        log.info("SSE: Client mới đã kết nối tới sự kiện ID {}. Tổng số client: {}", channelId,
                emitters.size());

        emitter.onCompletion(() -> removeEmitter(channelId, emitter, "COMPLETED"));
        emitter.onTimeout(() -> removeEmitter(channelId, emitter, "TIMED_OUT"));
        emitter.onError(e -> removeEmitter(channelId, emitter, "ERROR"));
    }

    private void removeEmitter(final Integer channelId, final SseEmitter emitter,
            final String reason) {
        final List<SseEmitter> emitters = this.emittersByChannelId.get(channelId);
        if (emitters != null) {
            emitters.remove(emitter);
            log.info(
                    "SSE: Client đã ngắt kết nối khỏi sự kiện ID {} vì lý do: {}. Số client còn lại: {}",
                    channelId, reason, emitters.size());

            if (emitters.isEmpty()) {
                this.emittersByChannelId.remove(channelId);
                log.info("SSE: Kênh ID {} không còn client nào, đã xóa kênh.", channelId);
            }
        }
    }

    @Override
    public void sendEventToClients(final Integer channelId, final String eventName,
            final Object data) {
        final List<SseEmitter> emitters = this.emittersByChannelId.get(channelId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("SSE: Không tìm thấy client nào cho kênh ID {} để gửi sự kiện '{}'",
                    channelId, eventName);
            return;
        }

        log.debug("SSE: Gửi sự kiện '{}' đến {} client(s) của kênh ID {}", eventName,
                emitters.size(), channelId);

        final List<SseEmitter> deadEmitters = new ArrayList<>();
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.warn("SSE: Lỗi khi gửi sự kiện đến 1 client, đánh dấu để xóa. Kênh ID {}",
                        channelId);
                deadEmitters.add(emitter);
            }
        });

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
            log.info("SSE: Đã xóa {} client không hoạt động khỏi kênh ID {}", deadEmitters.size(),
                    channelId);
        }
    }
}
