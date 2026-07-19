package kr.bi.go_to.batch.listener;

import kr.bi.go_to.batch.support.TourApiIncrementalSyncContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiIncrementalSyncLogListener implements JobExecutionListener {

    private final BatchSyncLogWriter batchSyncLogWriter;

    @Override
    public void afterJob(JobExecution jobExecution) {
        boolean completed = jobExecution.getStatus() == BatchStatus.COMPLETED;
        String syncLogDate = completed
                ? jobExecution.getExecutionContext().getString(TourApiIncrementalSyncContext.TARGET_DATE_KEY, null)
                : jobExecution.getExecutionContext().getString(TourApiIncrementalSyncContext.REQUEST_DATE_KEY, null);

        if (!StringUtils.hasText(syncLogDate)) {
            if (completed) {
                throw new IllegalStateException("Cannot write successful batch_sync_log without target date");
            }
            log.warn(
                    "Skipping batch_sync_log write because request date is missing. jobName={}, status={}",
                    TourApiIncrementalSyncContext.JOB_NAME,
                    jobExecution.getStatus());
            return;
        }

        String status =
                completed ? TourApiIncrementalSyncContext.STATUS_SUCCESS : TourApiIncrementalSyncContext.STATUS_FAIL;
        int processedCount = countProcessedIncrementalItems(jobExecution);

        batchSyncLogWriter.write(TourApiIncrementalSyncContext.JOB_NAME, syncLogDate, status, processedCount);
        log.info(
                "Wrote batch_sync_log. jobName={}, syncLogDate={}, status={}, processedCount={}",
                TourApiIncrementalSyncContext.JOB_NAME,
                syncLogDate,
                status,
                processedCount);
    }

    private int countProcessedIncrementalItems(JobExecution jobExecution) {
        return Math.toIntExact(jobExecution.getStepExecutions().stream()
                .filter(stepExecution ->
                        TourApiIncrementalSyncContext.BASE_STEP_NAME.equals(stepExecution.getStepName()))
                .mapToLong(StepExecution::getWriteCount)
                .sum());
    }
}
