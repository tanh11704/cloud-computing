package API_BoPhieu.service.import_job;

import API_BoPhieu.constants.ImportJobStatus;
import API_BoPhieu.entity.ImportJob;

public interface ImportJobService {
    ImportJob createImportJob(Integer eventId, String createdBy, String fileName);

    ImportJob getImportJobById(Integer jobId);

    ImportJob updateImportJobStatus(Integer jobId, ImportJobStatus status);

    ImportJob updateImportJobResult(Integer jobId, Integer totalRecords, Integer successCount,
            Integer skippedCount, String resultDetails);

    ImportJob updateImportJobError(Integer jobId, String errorMessage);

    ImportJob updateImportJobProgress(Integer jobId, Integer processedCount);

    ImportJob setTotalRecords(Integer jobId, Integer totalRecords);
}

