package kr.bi.go_to.batch.runner;

import kr.bi.go_to.batch.support.TourApiInitialLoadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "goto.batch.initial-load", name = "auto-run-enabled", havingValue = "true")
public class TourApiInitialLoadRunner {

    private final JobOperator jobOperator;
    private final Job tourApiInitialLoadJob;
    private final TourApiInitialLoadStatus initialLoadStatus;

    @EventListener(ApplicationReadyEvent.class)
    public void runInitialLoadIfNeeded() {
        if (initialLoadStatus.hasCompletedInitialLoad()) {
            log.info("tourApiInitialLoadJob already completed. Skipping automatic initial load.");
            return;
        }

        if (initialLoadStatus.hasRunningInitialLoad()) {
            log.info("tourApiInitialLoadJob is already running. Skipping automatic initial load.");
            return;
        }

        JobExecution lastExecution = initialLoadStatus.getLastInitialLoadExecution();
        if (lastExecution != null) {
            log.warn(
                    "No completed tourApiInitialLoadJob found. Last execution id={}, status={}. Retrying automatic initial load.",
                    lastExecution.getId(),
                    lastExecution.getStatus());
        }

        try {
            log.info("Starting automatic tourApiInitialLoadJob...");
            JobExecution execution = jobOperator.start(
                    tourApiInitialLoadJob,
                    new JobParametersBuilder()
                            .addString("trigger", "auto-initial-load")
                            .toJobParameters());
            log.info("Automatic tourApiInitialLoadJob finished with status {}.", execution.getStatus());
        } catch (Exception e) {
            log.error("Failed to run automatic tourApiInitialLoadJob", e);
        }
    }
}
