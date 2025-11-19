package API_BoPhieu.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import API_BoPhieu.constants.EventManagement;
import API_BoPhieu.repository.EventManagerRepo;
import lombok.RequiredArgsConstructor;

@Component("eventAuth")
@RequiredArgsConstructor
public class EventAuthorizationService {

    private final EventManagerRepo repo;

    public boolean hasEventRole(Authentication authentication, Integer eventId,
            EventManagement requiredRole) {

        String email = authentication.getName();
        return repo.findRoleTypeByEmail(eventId, email).map(EventManagement::valueOf)
                .filter(role -> role.atLeast(requiredRole)).isPresent();

    }
}
