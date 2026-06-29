package kr.bi.go_to.batch.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import kr.bi.go_to.batch.support.TourApiIncrementalSyncContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

class TourApiIncrementalSyncLogListenerTest {

    private BatchSyncLogWriter batchSyncLogWriter;
    private TourApiIncrementalSyncLogListener listener;

    @BeforeEach
    void setUp() {
        batchSyncLogWriter = mock(BatchSyncLogWriter.class);
        listener = new TourApiIncrementalSyncLogListener(batchSyncLogWriter);
    }

    @Test
    @DisplayName("증분 Job이 COMPLETED이면 afterJob에서 base step write count로 SUCCESS 로그를 남긴다")
    void writesSuccessLogWithIncrementalBaseStepWriteCount() {
        JobExecution jobExecution = jobExecution(BatchStatus.COMPLETED, "20260628", "20260629");
        jobExecution.addStepExecution(stepExecution(TourApiIncrementalSyncContext.BASE_STEP_NAME, jobExecution, 12));
        jobExecution.addStepExecution(stepExecution("tourApiDetailSyncStep", jobExecution, 7));

        listener.afterJob(jobExecution);

        verify(batchSyncLogWriter).write(TourApiIncrementalSyncContext.JOB_NAME, "20260629", "SUCCESS", 12);
    }

    @Test
    @DisplayName("증분 Job이 FAILED이면 afterJob에서 requestDate와 FAIL 상태로 로그를 남긴다")
    void writesFailLogWithRequestDateWhenJobDoesNotComplete() {
        JobExecution jobExecution = jobExecution(BatchStatus.FAILED, "20260628", "20260629");
        jobExecution.addStepExecution(stepExecution(TourApiIncrementalSyncContext.BASE_STEP_NAME, jobExecution, 3));

        listener.afterJob(jobExecution);

        verify(batchSyncLogWriter).write(TourApiIncrementalSyncContext.JOB_NAME, "20260628", "FAIL", 3);
    }

    @Test
    @DisplayName("COMPLETED Job에 targetDate가 없으면 afterJob에서 IllegalStateException이 난다")
    void throwsWhenCompletedJobHasNoTargetDate() {
        JobExecution jobExecution =
                new JobExecution(1L, new JobInstance(1L, TourApiIncrementalSyncContext.JOB_NAME), new JobParameters());
        jobExecution.setStatus(BatchStatus.COMPLETED);

        assertThatThrownBy(() -> listener.afterJob(jobExecution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("target date");

        verifyNoInteractions(batchSyncLogWriter);
    }

    @Test
    @DisplayName("FAILED Job에 targetDate가 없으면 afterJob은 로그 기록을 건너뛴다")
    void skipsFailedLogWhenTargetDateIsMissing() {
        JobExecution jobExecution =
                new JobExecution(1L, new JobInstance(1L, TourApiIncrementalSyncContext.JOB_NAME), new JobParameters());
        jobExecution.setStatus(BatchStatus.FAILED);

        listener.afterJob(jobExecution);

        verifyNoInteractions(batchSyncLogWriter);
    }

    private JobExecution jobExecution(BatchStatus status, String requestDate, String targetDate) {
        JobExecution jobExecution =
                new JobExecution(1L, new JobInstance(1L, TourApiIncrementalSyncContext.JOB_NAME), new JobParameters());
        jobExecution.setStatus(status);
        jobExecution.getExecutionContext().putString(TourApiIncrementalSyncContext.REQUEST_DATE_KEY, requestDate);
        jobExecution.getExecutionContext().putString(TourApiIncrementalSyncContext.TARGET_DATE_KEY, targetDate);
        return jobExecution;
    }

    private StepExecution stepExecution(String stepName, JobExecution jobExecution, long writeCount) {
        StepExecution stepExecution = new StepExecution(1L, stepName, jobExecution);
        stepExecution.setWriteCount(writeCount);
        return stepExecution;
    }
}
