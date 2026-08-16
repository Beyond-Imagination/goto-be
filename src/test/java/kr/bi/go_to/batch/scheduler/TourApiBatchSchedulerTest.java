package kr.bi.go_to.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;
import kr.bi.go_to.batch.support.TourApiInitialLoadStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.scheduling.annotation.Scheduled;

class TourApiBatchSchedulerTest {

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job incrementalJob = mock(Job.class);
    private final TourApiInitialLoadStatus initialLoadStatus = mock(TourApiInitialLoadStatus.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final PostgresAdvisoryLock executionLock = mock(PostgresAdvisoryLock.class);
    private final TourApiBatchScheduler scheduler =
            new TourApiBatchScheduler(jobOperator, incrementalJob, initialLoadStatus, jobRepository, executionLock);

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
        executeLockedTask();
        when(jobRepository.findRunningJobExecutions("tourApiIncrementalSyncJob"))
                .thenReturn(Set.of());
        when(jobOperator.start(any(Job.class), any(JobParameters.class))).thenReturn(jobExecution());

        scheduler.runTourApiSyncJob();

        verify(jobOperator).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("이미 증분 동기화 Job이 실행 중이면 스케줄러는 중복 실행하지 않는다")
    void skipsIncrementalSyncWhenAnotherExecutionIsRunning() throws Exception {
        when(initialLoadStatus.hasCompletedInitialLoad()).thenReturn(true);
        executeLockedTask();
        when(jobRepository.findRunningJobExecutions("tourApiIncrementalSyncJob"))
                .thenReturn(Set.of(jobExecution()));

        scheduler.runTourApiSyncJob();

        verifyNoInteractions(jobOperator);
    }

    @Test
    @DisplayName("다른 인스턴스가 advisory lock을 보유하면 실행 상태 확인과 Job 시작을 건너뛴다")
    void skipsIncrementalSyncWhenAnotherInstanceOwnsTheExecutionLock() throws Exception {
        when(initialLoadStatus.hasCompletedInitialLoad()).thenReturn(true);
        when(executionLock.executeIfAvailable(anyString(), any())).thenReturn(false);

        scheduler.runTourApiSyncJob();

        verifyNoInteractions(jobRepository, jobOperator);
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

    private void executeLockedTask() throws Exception {
        when(executionLock.executeIfAvailable(anyString(), any())).thenAnswer(invocation -> {
            invocation
                    .getArgument(1, PostgresAdvisoryLock.CheckedRunnable.class)
                    .run();
            return true;
        });
    }
}
