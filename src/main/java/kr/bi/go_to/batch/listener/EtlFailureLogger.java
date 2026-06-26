package kr.bi.go_to.batch.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EtlFailureLogger {

    private final JdbcTemplate jdbcTemplate;
    private static final String INSERT_FAILURE_LOG =
            "INSERT INTO etl_failure_log (external_id, error_message, created_at) VALUES (?, ?, NOW())";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String externalId, String errorMessage) {
        jdbcTemplate.update(INSERT_FAILURE_LOG, externalId, errorMessage);
    }
}
