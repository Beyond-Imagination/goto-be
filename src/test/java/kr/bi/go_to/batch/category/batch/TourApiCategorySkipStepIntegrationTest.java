package kr.bi.go_to.batch.category.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryException;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryReason;
import kr.bi.go_to.batch.listener.TourApiSkipListener;
import kr.bi.go_to.batch.writer.PlaceItemWriter;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TourApiCategorySkipStepIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PlaceItemWriter placeItemWriter;

    @Autowired
    private TourApiSkipListener skipListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM etl_failure_log");
        jdbcTemplate.update("DELETE FROM places");
        jdbcTemplate.update("DELETE FROM tour_api_categories");
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_categories
                    (code, parent_code, depth, name, active, last_seen_sync_token)
                VALUES
                    ('L1', NULL, 1, '대분류', TRUE, ?),
                    ('M1', 'L1', 2, '중분류', TRUE, ?),
                    ('S1', 'M1', 3, '소분류', TRUE, ?)
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    @Test
    @DisplayName("실제 Spring Batch step은 잘못된 분류 한 건을 기록·스킵하고 다음 정상 장소를 저장한다")
    void skipsInvalidCategoryAndContinuesWithNextValidPlace() throws Exception {
        Job job = job("categorySkipContinueJob", placeItemWriter);

        JobExecution execution = jobLauncher.run(job, uniqueParameters());
        StepExecution step = execution.getStepExecutions().iterator().next();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(step.getProcessSkipCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM etl_failure_log", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM places WHERE external_id = 'valid' AND category_code = 'S1'", Long.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("chunk rollback 후 같은 JobInstance를 재시작해도 분류 실패 로그와 정상 장소는 각각 한 건만 남는다")
    void restartAfterChunkRollbackDoesNotDuplicateFailureLogOrPlace() throws Exception {
        AtomicBoolean failFirstWrite = new AtomicBoolean(true);
        ItemWriter<PlaceProcessingResult> failOnceWriter = chunk -> {
            if (failFirstWrite.getAndSet(false)) {
                throw new IllegalStateException("forced chunk rollback");
            }
            placeItemWriter.write(chunk);
        };
        Job job = job("categorySkipRestartJob", failOnceWriter);
        JobParameters parameters = uniqueParameters();

        JobExecution failed = jobLauncher.run(job, parameters);
        JobExecution restarted = jobLauncher.run(job, parameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getStepExecutions().iterator().next().getProcessSkipCount())
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM etl_failure_log", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM places WHERE external_id = 'valid' AND category_code = 'S1'", Long.class))
                .isEqualTo(1);
    }

    private Job job(String jobName, ItemWriter<PlaceProcessingResult> writer) {
        Step step = new StepBuilder(jobName + "Step", jobRepository)
                .<TourApiItemDto, PlaceProcessingResult>chunk(2)
                .transactionManager(transactionManager)
                .reader(new RestartableListReader(List.of(item("invalid"), item("valid"))))
                .processor(item -> {
                    if ("invalid".equals(item.contentid())) {
                        throw new InvalidTourApiCategoryException(
                                InvalidTourApiCategoryReason.UNKNOWN_INACTIVE_OR_NON_LEAF,
                                item.contentid(),
                                item.lclsSystm3());
                    }
                    Place place = Place.builder()
                            .externalId(item.contentid())
                            .source("TOUR_API")
                            .categoryCode(item.lclsSystm3())
                            .name(item.title())
                            .build();
                    return new PlaceProcessingResult(place, null, null);
                })
                .writer(writer)
                .faultTolerant()
                .skip(InvalidTourApiCategoryException.class)
                .skipLimit(10)
                .listener(skipListener)
                .build();
        return new JobBuilder(jobName, jobRepository).start(step).build();
    }

    private JobParameters uniqueParameters() {
        return new JobParametersBuilder()
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();
    }

    private TourApiItemDto item(String contentId) {
        return new TourApiItemDto(
                contentId,
                "12",
                "장소 " + contentId,
                null,
                null,
                null,
                null,
                "L1",
                "M1",
                "S1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "1");
    }

    private static final class RestartableListReader implements ItemStreamReader<TourApiItemDto> {

        private static final String INDEX_KEY = "category.skip.test.index";

        private final List<TourApiItemDto> items;
        private int index;

        private RestartableListReader(List<TourApiItemDto> items) {
            this.items = items;
        }

        @Override
        public TourApiItemDto read() {
            return index < items.size() ? items.get(index++) : null;
        }

        @Override
        public void open(ExecutionContext executionContext) {
            index = executionContext.getInt(INDEX_KEY, 0);
        }

        @Override
        public void update(ExecutionContext executionContext) {
            executionContext.putInt(INDEX_KEY, index);
        }
    }
}
