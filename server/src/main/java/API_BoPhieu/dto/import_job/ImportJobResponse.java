package API_BoPhieu.dto.import_job;

import java.time.Instant;
import API_BoPhieu.constants.ImportJobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportJobResponse {
    private Integer id;
    private Integer eventId;
    private String createdBy;
    private String fileName;
    private ImportJobStatus status;
    private Integer totalRecords;
    private Integer processedCount;
    private Integer successCount;
    private Integer skippedCount;
    private String errorMessage;
    private String resultDetails;
    private Instant createdAt;
    private Instant updatedAt;

    public Double getProgressPercentage() {
        if (totalRecords == null || totalRecords == 0) {
            return 0.0;
        }
        final int processed = processedCount != null ? processedCount : 0;
        return Math.min(100.0, (processed * 100.0) / totalRecords);
    }
}

