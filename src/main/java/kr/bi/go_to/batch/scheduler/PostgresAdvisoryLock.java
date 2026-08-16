package kr.bi.go_to.batch.scheduler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostgresAdvisoryLock {

    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(hashtextextended(?, 0))";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(hashtextextended(?, 0))";

    private final DataSource dataSource;

    public boolean executeIfAvailable(String lockName, CheckedRunnable task) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!queryBoolean(connection, TRY_LOCK_SQL, lockName)) {
                return false;
            }

            Exception taskFailure = null;
            try {
                task.run();
                return true;
            } catch (Exception exception) {
                taskFailure = exception;
                throw exception;
            } finally {
                try {
                    if (!queryBoolean(connection, UNLOCK_SQL, lockName)) {
                        throw new SQLException("PostgreSQL advisory lock was not held during release");
                    }
                } catch (SQLException unlockFailure) {
                    if (taskFailure != null) {
                        taskFailure.addSuppressed(unlockFailure);
                    } else {
                        throw unlockFailure;
                    }
                }
            }
        }
    }

    private boolean queryBoolean(Connection connection, String sql, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("PostgreSQL advisory lock query returned no row");
                }
                return resultSet.getBoolean(1);
            }
        }
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }
}
