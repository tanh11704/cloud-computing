package API_BoPhieu.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import API_BoPhieu.entity.ChatSession;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Integer> {
    Optional<ChatSession> findByUserIdAndEventId(Integer userId, Integer eventId);
}

