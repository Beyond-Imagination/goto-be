package kr.bi.go_to.batch.support;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TourApiInitialLoadStatus {

    public static final String INITIAL_LOAD_JOB_NAME = "tourApiInitialLoadJob";

    private final JobRepository jobRepository;

    public boolean hasCompletedInitialLoad() {
        return jobRepository.findJobInstances(INITIAL_LOAD_JOB_NAME).stream()
                .flatMap(instance -> jobRepository.getJobExecutions(instance).stream())
                .anyMatch(execution -> execution.getStatus() == BatchStatus.COMPLETED);
    }

    public boolean hasRunningInitialLoad() {
        return jobRepository.findJobInstances(INITIAL_LOAD_JOB_NAME).stream()
                .flatMap(instance -> jobRepository.getJobExecutions(instance).stream())
                .anyMatch(JobExecution::isRunning);
    }

    public JobExecution getLastInitialLoadExecution() {
        JobInstance lastJobInstance = jobRepository.getLastJobInstance(INITIAL_LOAD_JOB_NAME);
        if (lastJobInstance == null) {
            return null;
        }
        return jobRepository.getLastJobExecution(lastJobInstance);
    }
}
