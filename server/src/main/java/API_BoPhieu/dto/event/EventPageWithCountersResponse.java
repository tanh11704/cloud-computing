package API_BoPhieu.dto.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import API_BoPhieu.dto.common.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public class EventPageWithCountersResponse {
    private PageResponse<EventResponse> pagination;
    private EventCountersResponse counters;
}
