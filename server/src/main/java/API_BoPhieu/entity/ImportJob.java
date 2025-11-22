package API_BoPhieu.entity;

import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import API_BoPhieu.constants.ImportJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, name = "event_id")
    private Integer eventId;

    @Column(nullable = false, name = "created_by")
    private String createdBy;

    @Column(nullable = false, name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "status")
    private ImportJobStatus status = ImportJobStatus.PENDING;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "processed_count")
    private Integer processedCount = 0;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "skipped_count")
    private Integer skippedCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "result_details", columnDefinition = "TEXT")
    private String resultDetails;

    @CreationTimestamp
    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    private Instant updatedAt;
}

