package API_BoPhieu.service.file;

import java.io.IOException;
import java.util.List;
import API_BoPhieu.dto.attendant.ParticipantResponse;
import API_BoPhieu.dto.poll.PollStatsWithEmailsResponse;

public interface FileExportService {

    byte[] exportParticipantsToExcel(final List<ParticipantResponse> participants,
            final String eventTitle) throws IOException;

    byte[] exportPollStatsToExcel(final PollStatsWithEmailsResponse pollStats,
            final String eventTitle) throws IOException;
}

