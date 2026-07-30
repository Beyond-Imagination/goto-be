package kr.bi.go_to.batch.scheduler;

import kr.bi.go_to.batch.support.TourApiIncrementalSyncContext;
import kr.bi.go_to.batch.support.TourApiInitialLoadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiBatchScheduler {

    private final JobOperator jobOperator;
    private final Job tourApiIncrementalSyncJob;
    private final TourApiInitialLoadStatus initialLoadStatus;
    private final JobRepository jobRepository;
    private final PostgresAdvisoryLock executionLock;

    /** 매일 오전 3시에 배치를 실행한다. */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Seoul")
    public void runTourApiSyncJob() {
        if (!initialLoadStatus.hasCompletedInitialLoad()) {
            log.warn("Skipping scheduled tourApiIncrementalSyncJob because tourApiInitialLoadJob has not completed.");
            return;
        }

        try {
            boolean lockAcquired = executionLock.executeIfAvailable(TourApiIncrementalSyncContext.JOB_NAME, () -> {
                if (!jobRepository
                        .findRunningJobExecutions(TourApiIncrementalSyncContext.JOB_NAME)
                        .isEmpty()) {
                    log.warn(
                            "Skipping scheduled tourApiIncrementalSyncJob because another execution is already running.");
                    return;
                }

                log.info("Starting scheduled tourApiIncrementalSyncJob...");
                jobOperator.start(
                        tourApiIncrementalSyncJob,
                        new JobParametersBuilder()
                                .addLong("time", System.currentTimeMillis())
                                .toJobParameters());
                log.info("Scheduled tourApiIncrementalSyncJob completed successfully.");
            });
            if (!lockAcquired) {
                log.warn("Skipping scheduled tourApiIncrementalSyncJob because another instance is starting it.");
            }
        } catch (Exception e) {
            log.error("Failed to run scheduled tourApiIncrementalSyncJob", e);
        }
    }
}
