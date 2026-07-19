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
        // given
        String externalId = "test-ext-123";
        String errorMessage = "Test error message";

        // when & then: 외부 트랜잭션 실행 중 예외를 발생시켜 롤백을 유도하고, 해당 예외가 올바르게 발생했는지 검증
        assertThatThrownBy(() -> outerTransactionTemplate.executeWithoutResult(status -> {
                    // REQUIRES_NEW 트랜잭션 실행
                    etlFailureLogger.logFailure(externalId, errorMessage);

                    // 의도적 예외 던짐으로 외부 트랜잭션 롤백 유도
                    throw new RuntimeException("Force rollback of outer transaction");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Force rollback of outer transaction");

        // then: 외부 트랜잭션은 롤백되었지만 failure log는 정상적으로 커밋되어 조회되어야 함
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM etl_failure_log WHERE external_id = ?", Integer.class, externalId);
        assertThat(count).isEqualTo(1);
    }
}
