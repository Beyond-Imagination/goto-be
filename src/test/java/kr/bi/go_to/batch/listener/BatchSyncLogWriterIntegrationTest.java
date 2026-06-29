package kr.bi.go_to.batch.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import kr.bi.go_to.batch.support.TourApiIncrementalSyncContext;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BatchSyncLogWriterIntegrationTest {

    @Autowired
    private BatchSyncLogWriter batchSyncLogWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate outerTransactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM batch_sync_log");
        outerTransactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백되어도 REQUIRES_NEW인 BatchSyncLogWriter의 동기화 로그는 저장되어야 한다")
    void write_runsInNewTransaction_isNotRolledBackWhenOuterTransactionRollsBack() {
        String jobName = TourApiIncrementalSyncContext.JOB_NAME;
        String targetDate = "20260629";
        String status = TourApiIncrementalSyncContext.STATUS_SUCCESS;
        int processedCount = 15;

        assertThatThrownBy(() -> outerTransactionTemplate.executeWithoutResult(transactionStatus -> {
                    batchSyncLogWriter.write(jobName, targetDate, status, processedCount);
                    throw new RuntimeException("Force rollback of outer transaction");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Force rollback of outer transaction");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT job_name, target_date, status, processed_count
                FROM batch_sync_log
                WHERE job_name = ? AND target_date = ?
                """,
                jobName,
                targetDate);

        assertThat(row.get("job_name")).isEqualTo(jobName);
        assertThat(row.get("target_date")).isEqualTo(targetDate);
        assertThat(row.get("status")).isEqualTo(status);
        assertThat(((Number) row.get("processed_count")).intValue()).isEqualTo(processedCount);
    }
}
