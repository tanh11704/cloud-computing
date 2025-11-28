package API_BoPhieu.dto.ai;

import java.time.Instant;
import java.util.List;
import API_BoPhieu.constants.ContentLength;
import API_BoPhieu.constants.ContentTarget;
import API_BoPhieu.constants.ContentTone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateDescriptionRequest {
    @NotBlank(message = "")
    private String title;

    private String location;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private Instant startTime;

    private Instant endTime;

    private List<String> speakers; // Danh sách diễn giả (optional)

    private String additionalInfo; // Thông tin bổ sung (optional)

    @NotNull(message = "Giọng văn không được để trống")
    private ContentTone tone;

    @NotNull(message = "Độ dài không được để trống")
    private ContentLength length;

    @NotNull(message = "Nền tảng đích không được để trống")
    private ContentTarget target;
}

