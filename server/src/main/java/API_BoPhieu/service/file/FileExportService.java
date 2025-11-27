package API_BoPhieu.service.file;

import java.io.IOException;
import java.util.List;
import API_BoPhieu.dto.attendant.ParticipantResponse;

/**
 * Service interface for exporting data to Excel files. Follows clean architecture principles with
 * separation of concerns.
 */
public interface FileExportService {

    /**
     * Exports a list of participants to an Excel file.
     *
     * @param participants List of participants to export
     * @param eventTitle Title of the event (used in filename)
     * @return Byte array representing the Excel file (.xlsx format)
     * @throws IOException if file creation fails
     */
    byte[] exportParticipantsToExcel(final List<ParticipantResponse> participants,
            final String eventTitle) throws IOException;
}

