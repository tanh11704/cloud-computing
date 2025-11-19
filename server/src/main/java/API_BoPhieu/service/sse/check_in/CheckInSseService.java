package API_BoPhieu.service.sse.check_in;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface CheckInSseService {
    void addEmitter(Integer eventId, SseEmitter emitter);

    void sendEventToClients(Integer eventId, String eventName, Object data);
}
