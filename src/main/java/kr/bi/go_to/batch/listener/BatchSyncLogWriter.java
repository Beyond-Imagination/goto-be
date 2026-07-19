package kr.bi.go_to.batch.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BatchSyncLogWriter {

    private static final String INSERT_SYNC_LOG =
            "INSERT INTO batch_sync_log (job_name, target_date, status, processed_count, created_at) "
                    + "VALUES (?, ?, ?, ?, NOW())";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String jobName, String targetDate, String status, int processedCount) {
        jdbcTemplate.update(INSERT_SYNC_LOG, jobName, targetDate, status, processedCount);
    }
}
