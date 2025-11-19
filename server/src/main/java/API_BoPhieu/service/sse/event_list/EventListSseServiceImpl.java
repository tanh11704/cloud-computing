package API_BoPhieu.service.sse.event_list;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EventListSseServiceImpl implements EventListSseService {
    private final List<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();

    private static final String EVENT_NAME = "event_list_updated";
    private static final String EVENT_DATA = "{\"status\": \"needs_refresh\"}";

    @Override
    public void addEmitter(SseEmitter emitter) {
        this.globalEmitters.add(emitter);
        log.info("SSE-GLOBAL: Client mới đã kết nối. Tổng số: {}", globalEmitters.size());

        emitter.onCompletion(() -> removeEmitter(emitter, "COMPLETED"));
        emitter.onTimeout(() -> removeEmitter(emitter, "TIMED_OUT"));
        emitter.onError(e -> removeEmitter(emitter, "ERROR"));
    }

    private void removeEmitter(final SseEmitter emitter, final String reason) {
        final boolean removed = this.globalEmitters.remove(emitter);
        if (removed) {
            log.info("SSE-GLOBAL: Client đã ngắt kết nối (Lý do: {}). Còn lại: {} client(s)",
                    reason, globalEmitters.size());
        }
    }

    @Override
    public void dispatchListUpdate() {
        log.debug("SSE-GLOBAL: Bắt đầu gửi thông báo '{}' đến {} client(s)", EVENT_NAME,
                globalEmitters.size());

        for (final SseEmitter emitter : this.globalEmitters) {
            try {
                SseEmitter.SseEventBuilder eventBuilder =
                        SseEmitter.event().name(EVENT_NAME).data(EVENT_DATA);

                emitter.send(eventBuilder);
            } catch (IOException e) {
                log.warn("SSE-GLOBAL: Lỗi khi gửi SSE, có thể client đã ngắt kết nối: {}",
                        e.getMessage());
            }
        }
    }

}
