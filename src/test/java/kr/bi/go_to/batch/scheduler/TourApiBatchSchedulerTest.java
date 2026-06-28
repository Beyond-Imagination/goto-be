package kr.bi.go_to.batch.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import kr.bi.go_to.batch.support.TourApiInitialLoadStatus;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

class TourApiBatchSchedulerTest {

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job incrementalJob = mock(Job.class);
    private final TourApiInitialLoadStatus initialLoadStatus = mock(TourApiInitialLoadStatus.class);
    private final TourApiBatchScheduler scheduler =
            new TourApiBatchScheduler(jobOperator, incrementalJob, initialLoadStatus);

    @Test
    void skipsIncrementalSyncWhenInitialLoadHasNotCompleted() {
        when(initialLoadStatus.hasCompletedInitialLoad()).thenReturn(false);

        scheduler.runTourApiSyncJob();

        verifyNoInteractions(jobOperator);
    }

    @Test
    void startsIncrementalSyncWhenInitialLoadHasCompleted() throws Exception {
        when(initialLoadStatus.hasCompletedInitialLoad()).thenReturn(true);
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(jobExecution());

        scheduler.runTourApiSyncJob();

        verify(jobOperator).start(any(Job.class), any(JobParameters.class));
    }

    private JobExecution jobExecution() {
        return new JobExecution(1L, new JobInstance(1L, "tourApiIncrementalSyncJob"), new JobParameters());
    }
}
