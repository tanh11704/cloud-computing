package API_BoPhieu.service.sse.event_list;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface EventListSseService {
    void addEmitter(SseEmitter emitter);

    void dispatchListUpdate();
}
