package kr.bi.go_to.batch.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchSyncLogWriterTest {

    private JdbcTemplate jdbcTemplate;
    private BatchSyncLogWriter writer;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        writer = new BatchSyncLogWriter(jdbcTemplate);
    }

    @Test
    void writesBatchSyncLog() {
        writer.write("tourApiIncrementalSyncJob", "20260628", "SUCCESS", 15);

        verify(jdbcTemplate)
                .update(
                        "INSERT INTO batch_sync_log (job_name, target_date, status, processed_count, created_at) "
                                + "VALUES (?, ?, ?, ?, NOW())",
                        "tourApiIncrementalSyncJob",
                        "20260628",
                        "SUCCESS",
                        15);
    }
}
