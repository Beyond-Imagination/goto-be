package kr.bi.go_to.batch.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class EtlFailureLoggerIntegrationTest {

    @Autowired
    private EtlFailureLogger etlFailureLogger;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate outerTransactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM etl_failure_log");
        outerTransactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("외부 트랜잭션이 롤백된 뒤 EtlFailureLogger.logFailure를 호출하면 REQUIRES_NEW로 실패 로그만 커밋된다")
    void logFailure_runsInNewTransaction_isNotRolledBackWhenOuterTransactionRollsBack() {
        String externalId = "test-ext-123";
        String errorMessage = "Test error message";

        assertThatThrownBy(() -> outerTransactionTemplate.executeWithoutResult(status -> {
                    // 실패 로그는 별도 트랜잭션에서 커밋한다.
                    etlFailureLogger.logFailure(externalId, errorMessage);

                    throw new RuntimeException("Force rollback of outer transaction");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Force rollback of outer transaction");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM etl_failure_log WHERE external_id = ?", Integer.class, externalId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 JobInstance·step·항목·오류를 다시 기록하면 idempotency 제약으로 한 건만 유지한다")
    void logFailureDeduplicatesSameBatchItemAcrossRestart() {
        etlFailureLogger.logFailure(10L, "tourApiBaseSyncStep", "12345", "invalid category");
        etlFailureLogger.logFailure(10L, "tourApiBaseSyncStep", "12345", "invalid category");

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM etl_failure_log
                WHERE job_instance_id = 10
                  AND step_name = 'tourApiBaseSyncStep'
                  AND external_id = '12345'
                """,
                Long.class);

        assertThat(count).isEqualTo(1);
    }
}
