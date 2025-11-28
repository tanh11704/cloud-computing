package API_BoPhieu.dto.poll;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for option statistics with voter emails. Used for export functionality.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionStatsWithEmailsResponse {
    private Integer id;
    private String content;
    private Integer voteCount;
    private Double percentage;
    private List<String> voterEmails; // Danh sách email của người đã vote cho option này
}

