package API_BoPhieu.service.email;

import API_BoPhieu.entity.Event;
import API_BoPhieu.entity.User;

public interface EmailService {
    void sendPasswordResetEmail(User user, String token);

    void sendEventJoinNotificationEmail(User user, Event event);
}
