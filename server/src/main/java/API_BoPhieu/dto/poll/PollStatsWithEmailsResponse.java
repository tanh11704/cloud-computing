package API_BoPhieu.dto.poll;

import java.time.Instant;
import java.util.List;
import API_BoPhieu.constants.PollType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for poll statistics with voter emails. Used for export functionality.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PollStatsWithEmailsResponse {
    private Integer id;
    private String title;
    private PollType pollType;
    private Boolean isDelete;
    private Integer totalVotes;
    private Integer totalVoters;
    private List<OptionStatsWithEmailsResponse> options;
    private Instant startTime;
    private Instant endTime;
}

