package API_BoPhieu.service.attendant;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import API_BoPhieu.dto.attendant.ParticipantResponse;
import API_BoPhieu.dto.attendant.ParticipantsDto;
import API_BoPhieu.entity.Attendant;

public interface AttendantService {

    List<ParticipantResponse> getParticipantByEventId(Integer eventId);

    Attendant checkIn(String eventToken, String userEmail);

    byte[] generateQrCheck(Integer eventId) throws Exception;

    void deleteParticipantByEventIdAndUserId(Integer eventId, Integer userId);

    List<ParticipantResponse> addParticipants(Integer eventId, ParticipantsDto participantsDto,
            String adderEmail);

    Map<String, Object> importParticipants(Integer eventId, MultipartFile file,
            String managerEmail);

    void importParticipantsAsync(Integer eventId, byte[] fileContent, String fileName,
            String managerEmail, Integer jobId);

    void deleteParticipantsByEventIdAndUsersId(Integer eventId, ParticipantsDto participantsDto,
            String removerEmail);

    void cancelMyRegistration(Integer eventId, String userEmail);

    /**
     * Exports participants to Excel file with optional filtering.
     *
     * @param eventId Event ID to export participants from
     * @param filter Filter type: "all" for all participants, "checked-in" for checked-in only
     * @return Byte array representing the Excel file (.xlsx format)
     * @throws Exception if export fails
     */
    byte[] exportParticipantsToExcel(Integer eventId, String filter) throws Exception;
}
