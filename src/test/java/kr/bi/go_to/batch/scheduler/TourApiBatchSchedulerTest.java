package kr.bi.go_to.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import kr.bi.go_to.batch.support.TourApiInitialLoadStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;

class TourApiBatchSchedulerTest {

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job incrementalJob = mock(Job.class);
    private final TourApiInitialLoadStatus initialLoadStatus = mock(TourApiInitialLoadStatus.class);
    private final TourApiBatchScheduler scheduler =
            new TourApiBatchScheduler(jobOperator, incrementalJob, initialLoadStatus);

    @Test
    @DisplayName("초기 적재가 끝나지 않았으면 스케줄러는 증분 동기화 Job을 시작하지 않는다")
    void skipsIncrementalSyncWhenInitialLoadHasNotCompleted() {
        when(initialLoadStatus.hasCompletedInitialLoad()).thenReturn(false);

        scheduler.runTourApiSyncJob();

        verifyNoInteractions(jobOperator);
    }

    @Test
    @DisplayName("초기 적재가 끝났으면 스케줄러는 증분 동기화 Job을 시작한다")
    void startsIncrementalSyncWhenInitialLoadHasCompleted() throws Exception {
        when(initialLoadStatus.hasCompletedInitialLoad()).thenReturn(true);
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(jobExecution());

        scheduler.runTourApiSyncJob();

        verify(jobOperator).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("스케줄 메서드는 Asia/Seoul(KST) 타임존을 쓴다")
    void scheduledSyncUsesKstZone() throws Exception {
        Method method = TourApiBatchScheduler.class.getDeclaredMethod("runTourApiSyncJob");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    private JobExecution jobExecution() {
        return new JobExecution(1L, new JobInstance(1L, "tourApiIncrementalSyncJob"), new JobParameters());
    }
}
