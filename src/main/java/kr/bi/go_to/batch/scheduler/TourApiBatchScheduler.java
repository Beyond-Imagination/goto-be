package kr.bi.go_to.batch.scheduler;

import kr.bi.go_to.batch.support.TourApiInitialLoadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("default")
@RequiredArgsConstructor
public class TourApiBatchScheduler {

    private final JobOperator jobOperator;
    private final Job tourApiIncrementalSyncJob;
    private final TourApiInitialLoadStatus initialLoadStatus;

    /**
     * 매일 새벽 3시에 배치 실행.
     * Staggered 방식으로 빈 곳을 채워넣습니다.
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Seoul")
    public void runTourApiSyncJob() {
        if (!initialLoadStatus.hasCompletedInitialLoad()) {
            log.warn("Skipping scheduled tourApiIncrementalSyncJob because tourApiInitialLoadJob has not completed.");
            return;
        }

        log.info("Starting scheduled tourApiIncrementalSyncJob...");
        try {
            jobOperator.start(
                    tourApiIncrementalSyncJob,
                    new JobParametersBuilder()
                            .addLong("time", System.currentTimeMillis())
                            .toJobParameters());
            log.info("Scheduled tourApiIncrementalSyncJob completed successfully.");
        } catch (Exception e) {
            log.error("Failed to run scheduled tourApiIncrementalSyncJob", e);
        }
    }
}
