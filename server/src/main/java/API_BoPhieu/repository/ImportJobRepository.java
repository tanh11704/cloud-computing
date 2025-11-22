package API_BoPhieu.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import API_BoPhieu.entity.ImportJob;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Integer> {
    Optional<ImportJob> findByIdAndCreatedBy(Integer id, String createdBy);
}

