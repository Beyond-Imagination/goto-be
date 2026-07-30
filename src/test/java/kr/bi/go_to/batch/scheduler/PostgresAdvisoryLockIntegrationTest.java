package kr.bi.go_to.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PostgresAdvisoryLockIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void onlyOneConnectionCanExecuteTheNamedCriticalSection() throws Exception {
        PostgresAdvisoryLock firstLock = new PostgresAdvisoryLock(dataSource);
        PostgresAdvisoryLock secondLock = new PostgresAdvisoryLock(dataSource);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondExecuted = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Boolean> first = executor.submit(() -> firstLock.executeIfAvailable("job-lock-test", () -> {
                firstEntered.countDown();
                assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
            }));

            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondLock.executeIfAvailable("job-lock-test", () -> secondExecuted.set(true)))
                    .isFalse();
            assertThat(secondExecuted).isFalse();

            releaseFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void releasesTheNamedLockWhenTheCriticalSectionFails() throws Exception {
        PostgresAdvisoryLock lock = new PostgresAdvisoryLock(dataSource);

        assertThatThrownBy(() -> lock.executeIfAvailable("failing-job-lock-test", () -> {
                    throw new IllegalStateException("expected failure");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("expected failure");

        AtomicBoolean nextExecuted = new AtomicBoolean();
        assertThat(lock.executeIfAvailable("failing-job-lock-test", () -> nextExecuted.set(true)))
                .isTrue();
        assertThat(nextExecuted).isTrue();
    }
}
