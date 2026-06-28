package kr.bi.go_to.batch.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;

class TourApiInitialLoadStatusTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final TourApiInitialLoadStatus status = new TourApiInitialLoadStatus(jobRepository);

    @Test
    void hasCompletedInitialLoadReturnsTrueWhenAnyExecutionCompleted() {
        JobInstance jobInstance = new JobInstance(1L, TourApiInitialLoadStatus.INITIAL_LOAD_JOB_NAME);
        JobExecution failedExecution = jobExecution(1L, jobInstance, BatchStatus.FAILED);
        JobExecution completedExecution = jobExecution(2L, jobInstance, BatchStatus.COMPLETED);

        when(jobRepository.findJobInstances(TourApiInitialLoadStatus.INITIAL_LOAD_JOB_NAME))
                .thenReturn(List.of(jobInstance));
        when(jobRepository.getJobExecutions(jobInstance)).thenReturn(List.of(failedExecution, completedExecution));

        assertThat(status.hasCompletedInitialLoad()).isTrue();
    }

    @Test
    void hasCompletedInitialLoadReturnsFalseWhenThereAreNoCompletedExecutions() {
        JobInstance jobInstance = new JobInstance(1L, TourApiInitialLoadStatus.INITIAL_LOAD_JOB_NAME);
        JobExecution failedExecution = jobExecution(1L, jobInstance, BatchStatus.FAILED);

        when(jobRepository.findJobInstances(TourApiInitialLoadStatus.INITIAL_LOAD_JOB_NAME))
                .thenReturn(List.of(jobInstance));
        when(jobRepository.getJobExecutions(jobInstance)).thenReturn(List.of(failedExecution));

        assertThat(status.hasCompletedInitialLoad()).isFalse();
    }

    @Test
    void hasRunningInitialLoadReturnsTrueWhenAnyExecutionIsRunning() {
        JobInstance jobInstance = new JobInstance(1L, TourApiInitialLoadStatus.INITIAL_LOAD_JOB_NAME);
        JobExecution runningExecution = jobExecution(1L, jobInstance, BatchStatus.STARTED);

        when(jobRepository.findJobInstances(TourApiInitialLoadStatus.INITIAL_LOAD_JOB_NAME))
                .thenReturn(List.of(jobInstance));
        when(jobRepository.getJobExecutions(jobInstance)).thenReturn(List.of(runningExecution));

        assertThat(status.hasRunningInitialLoad()).isTrue();
    }

    private JobExecution jobExecution(long id, JobInstance jobInstance, BatchStatus batchStatus) {
        JobExecution jobExecution = new JobExecution(id, jobInstance, new JobParameters());
        jobExecution.setStatus(batchStatus);
        return jobExecution;
    }
}
