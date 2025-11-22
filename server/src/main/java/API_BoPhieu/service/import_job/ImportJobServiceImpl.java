package API_BoPhieu.service.import_job;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import API_BoPhieu.constants.ImportJobStatus;
import API_BoPhieu.entity.ImportJob;
import API_BoPhieu.exception.NotFoundException;
import API_BoPhieu.repository.ImportJobRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportJobServiceImpl implements ImportJobService {

    private final ImportJobRepository importJobRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public ImportJob createImportJob(final Integer eventId, final String createdBy,
            final String fileName) {
        log.debug("Creating import job for event ID: {}, created by: {}, file: {}", eventId,
                createdBy, fileName);
        final ImportJob job = new ImportJob();
        job.setEventId(eventId);
        job.setCreatedBy(createdBy);
        job.setFileName(fileName);
        job.setStatus(ImportJobStatus.PENDING);
        return importJobRepository.save(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportJob getImportJobById(final Integer jobId) {
        return importJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Import job not found: " + jobId));
    }

    @Override
    @Transactional
    public ImportJob updateImportJobStatus(final Integer jobId, final ImportJobStatus status) {
        log.info("Updating import job {} status to {}", jobId, status);
        final ImportJob job = getImportJobById(jobId);
        job.setStatus(status);
        final ImportJob saved = importJobRepository.save(job);
        entityManager.flush(); // Force flush to ensure status is visible immediately
        return saved;
    }

    @Override
    @Transactional
    public ImportJob updateImportJobResult(final Integer jobId, final Integer totalRecords,
            final Integer successCount, final Integer skippedCount, final String resultDetails) {
        log.debug("Updating import job {} result: total={}, success={}, skipped={}", jobId,
                totalRecords, successCount, skippedCount);
        final ImportJob job = getImportJobById(jobId);
        if (totalRecords != null) {
            job.setTotalRecords(totalRecords);
        }
        if (successCount != null) {
            job.setSuccessCount(successCount);
        }
        if (skippedCount != null) {
            job.setSkippedCount(skippedCount);
        }
        if (resultDetails != null) {
            job.setResultDetails(resultDetails);
        }
        if (successCount != null || skippedCount != null) {
            job.setStatus(ImportJobStatus.COMPLETED);
            job.setProcessedCount(job.getTotalRecords()); // Mark as 100% complete
        }
        return importJobRepository.save(job);
    }

    @Override
    @Transactional
    public ImportJob updateImportJobError(final Integer jobId, final String errorMessage) {
        log.error("Updating import job {} with error: {}", jobId, errorMessage);
        final ImportJob job = getImportJobById(jobId);
        job.setStatus(ImportJobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        return importJobRepository.save(job);
    }

    @Override
    @Transactional
    public ImportJob updateImportJobProgress(final Integer jobId, final Integer processedCount) {
        final ImportJob job = getImportJobById(jobId);
        job.setProcessedCount(processedCount);
        final ImportJob saved = importJobRepository.save(job);
        entityManager.flush(); // Force flush for real-time progress updates
        return saved;
    }

    @Override
    @Transactional
    public ImportJob setTotalRecords(final Integer jobId, final Integer totalRecords) {
        log.debug("Setting total records for import job {}: {}", jobId, totalRecords);
        final ImportJob job = getImportJobById(jobId);
        job.setTotalRecords(totalRecords);
        return importJobRepository.save(job);
    }
}

