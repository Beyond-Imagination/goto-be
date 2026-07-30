package kr.bi.go_to.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import kr.bi.go_to.batch.support.TourApiIncrementalSyncContext;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBatchTest
@SpringBootTest(
        properties = {"tour-api.detail-quota=10", "tour-api.category-page-size=10000", "tour-api.detail-concurrency=1"})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "TOUR_API_JOB_MANUAL_E2E_ENABLED", matches = "true")
class TourApiJobProductionFixtureManualE2ETest {

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private Job tourApiIncrementalSyncJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("대표 production fixture를 Testcontainers로 복사해 live lazy-backfill smoke를 두 번 실행한다")
    void runsLiveLazyBackfillSmokeTwiceAgainstReadOnlyProductionFixtures() throws Exception {
        List<ProductionPlace> fixtures = readProductionFixtures();
        assertThat(fixtures).hasSize(10);

        insertFixtures(fixtures);
        jdbcTemplate.update(
                """
                INSERT INTO batch_sync_log (job_name, target_date, status, processed_count)
                VALUES (?, '20991231', 'SUCCESS', 0)
                """,
                TourApiIncrementalSyncContext.JOB_NAME);
        report("BEFORE");

        jobOperatorTestUtils.setJob(tourApiIncrementalSyncJob);
        JobExecution first = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("manual.run.id", UUID.randomUUID().toString())
                .toJobParameters());
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        report("AFTER_FIRST");

        Map<String, Long> terminalCounts = statusCounts();
        JobExecution second = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("manual.run.id", UUID.randomUUID().toString())
                .toJobParameters());
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(statusCounts()).containsAllEntriesOf(terminalCounts);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT read_count
                        FROM batch_step_execution
                        WHERE step_name = 'tourApiDetailSyncStep'
                        ORDER BY step_execution_id DESC
                        LIMIT 1
                        """,
                        Long.class))
                .isZero();
        report("AFTER_SECOND");
    }

    private List<ProductionPlace> readProductionFixtures() throws Exception {
        String fixturePath = System.getenv("TOUR_API_FIXTURE_JSON");
        if (hasText(fixturePath)) {
            return readJsonFixtures(Path.of(fixturePath));
        }

        String url = requiredEnvironment("PROD_DB_URL");
        try (Connection connection = DriverManager.getConnection(
                url, requiredEnvironment("PROD_DB_USERNAME"), requiredEnvironment("PROD_DB_PASSWORD"))) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (PreparedStatement readOnly = connection.prepareStatement("SET TRANSACTION READ ONLY")) {
                readOnly.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                            """
                            (SELECT external_id, source, category_code, name, sanitized_address,
                                    ST_AsText(location_point), thumbnail_url, content_type_id, tel,
                                    detail_common_synced, detail_with_tour_synced, detail_intro_synced
                             FROM places
                             WHERE source = 'TOUR_API' AND is_deleted = false
                               AND (category_code IS NULL OR BTRIM(category_code) = '')
                             LIMIT 4)
                            UNION ALL
                            (SELECT external_id, source, category_code, name, sanitized_address,
                                    ST_AsText(location_point), thumbnail_url, content_type_id, tel,
                                    detail_common_synced, detail_with_tour_synced, detail_intro_synced
                             FROM places
                             WHERE source = 'TOUR_API' AND is_deleted = false
                               AND category_code IS NOT NULL AND BTRIM(category_code) <> ''
                             ORDER BY detail_common_synced, detail_with_tour_synced, detail_intro_synced
                             LIMIT 6)
                            """);
                    ResultSet resultSet = statement.executeQuery()) {
                List<ProductionPlace> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new ProductionPlace(
                            resultSet.getString(1),
                            resultSet.getString(2),
                            resultSet.getString(3),
                            resultSet.getString(4),
                            resultSet.getString(5),
                            resultSet.getString(6),
                            resultSet.getString(7),
                            resultSet.getString(8),
                            resultSet.getString(9),
                            resultSet.getBoolean(10),
                            resultSet.getBoolean(11),
                            resultSet.getBoolean(12)));
                }
                connection.rollback();
                return rows;
            }
        }
    }

    private List<ProductionPlace> readJsonFixtures(Path fixturePath) throws Exception {
        JsonNode root;
        try (InputStream input = Files.newInputStream(fixturePath)) {
            root = objectMapper.readTree(input);
        }
        assertThat(root.isArray()).as("fixture root must be a JSON array").isTrue();

        List<JsonNode> selected = new ArrayList<>();
        addMatches(selected, root, this::hasBlankCategory, 4);
        addMatches(
                selected,
                root,
                place -> synced(place, "detail_common_synced")
                        && synced(place, "detail_with_tour_synced")
                        && !synced(place, "detail_intro_synced"),
                1);
        addMatches(
                selected,
                root,
                place -> synced(place, "detail_common_synced")
                        && !synced(place, "detail_with_tour_synced")
                        && synced(place, "detail_intro_synced"),
                1);
        addMatches(
                selected,
                root,
                place -> !synced(place, "detail_common_synced")
                        && !synced(place, "detail_with_tour_synced")
                        && !synced(place, "detail_intro_synced")
                        && !hasBlankCategory(place),
                4);

        assertThat(selected)
                .as("fixture must supply the requested 4+1+1+4 representative rows")
                .hasSize(10);
        return selected.stream().map(this::toProductionPlace).toList();
    }

    private void addMatches(List<JsonNode> selected, JsonNode root, Predicate<JsonNode> predicate, int limit) {
        int added = 0;
        for (JsonNode place : root) {
            if (added == limit) {
                return;
            }
            boolean alreadySelected = selected.stream()
                    .anyMatch(existing -> text(existing, "external_id").equals(text(place, "external_id")));
            if (!alreadySelected
                    && "TOUR_API".equals(text(place, "source"))
                    && !place.path("is_deleted").asBoolean()
                    && predicate.test(place)) {
                selected.add(place);
                added++;
            }
        }
    }

    private ProductionPlace toProductionPlace(JsonNode place) {
        return new ProductionPlace(
                text(place, "external_id"),
                text(place, "source"),
                nullableText(place, "category_code"),
                text(place, "name"),
                nullableText(place, "sanitized_address"),
                nullableText(place, "location_point"),
                nullableText(place, "thumbnail_url"),
                nullableText(place, "content_type_id"),
                nullableText(place, "tel"),
                synced(place, "detail_common_synced"),
                synced(place, "detail_with_tour_synced"),
                synced(place, "detail_intro_synced"));
    }

    private boolean hasBlankCategory(JsonNode place) {
        return !hasText(nullableText(place, "category_code"));
    }

    private boolean synced(JsonNode place, String field) {
        return place.path(field).asBoolean();
    }

    private String text(JsonNode place, String field) {
        return place.path(field).asString();
    }

    private String nullableText(JsonNode place, String field) {
        JsonNode value = place.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private void insertFixtures(List<ProductionPlace> fixtures) {
        jdbcTemplate.execute("ALTER TABLE places DISABLE TRIGGER ALL");
        try {
            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO places (
                        external_id, source, category_code, name, sanitized_address, location_point,
                        thumbnail_url, content_type_id, tel, is_deleted,
                        detail_common_synced, detail_with_tour_synced, detail_intro_synced,
                        category_resolution_status, detail_common_status,
                        detail_with_tour_status, detail_intro_status, created_at, updated_at)
                    VALUES (?, ?, NULLIF(?, ''), ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, false,
                            ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """,
                    fixtures,
                    fixtures.size(),
                    (statement, place) -> {
                        statement.setString(1, place.externalId());
                        statement.setString(2, place.source());
                        statement.setString(3, place.categoryCode());
                        statement.setString(4, place.name());
                        statement.setString(5, place.address());
                        statement.setString(6, place.locationWkt());
                        statement.setString(7, place.thumbnailUrl());
                        statement.setString(8, place.contentTypeId());
                        statement.setString(9, place.tel());
                        statement.setBoolean(10, place.commonSynced());
                        statement.setBoolean(11, place.withTourSynced());
                        statement.setBoolean(12, place.introSynced());
                        statement.setString(13, hasText(place.categoryCode()) ? "RESOLVED" : "PENDING");
                        statement.setString(14, place.commonSynced() ? "SUCCESS" : "PENDING");
                        statement.setString(15, place.withTourSynced() ? "SUCCESS" : "PENDING");
                        statement.setString(16, place.introSynced() ? "SUCCESS" : "PENDING");
                    });
        } finally {
            jdbcTemplate.execute("ALTER TABLE places ENABLE TRIGGER ALL");
        }
    }

    private Map<String, Long> statusCounts() {
        return jdbcTemplate.query(
                """
                SELECT status, COUNT(*)
                FROM (
                    SELECT detail_common_status AS status FROM places
                    UNION ALL SELECT detail_with_tour_status FROM places
                    UNION ALL SELECT detail_intro_status FROM places
                ) states
                WHERE status <> 'PENDING'
                GROUP BY status
                """,
                resultSet -> {
                    Map<String, Long> result = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString(1), resultSet.getLong(2));
                    }
                    return result;
                });
    }

    private void report(String phase) {
        System.out.printf("%n=== TOUR API MANUAL E2E %s ===%n", phase);
        jdbcTemplate
                .queryForList(
                        """
                        SELECT external_id, category_code, category_resolution_status,
                               detail_common_status, detail_with_tour_status, detail_intro_status
                        FROM places ORDER BY external_id
                        """)
                .forEach(System.out::println);
        System.out.println(
                "place_bf_info=" + jdbcTemplate.queryForList("SELECT place_id, last_synced_at FROM place_bf_info"));
        System.out.println("etl_failure_log="
                + jdbcTemplate.queryForList("SELECT external_id, error_message FROM etl_failure_log"));
        System.out.println(
                "batch_steps="
                        + jdbcTemplate.queryForList(
                                "SELECT step_name, status, read_count, write_count FROM batch_step_execution ORDER BY step_execution_id"));
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertThat(value).as("%s must be set", name).isNotBlank();
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ProductionPlace(
            String externalId,
            String source,
            String categoryCode,
            String name,
            String address,
            String locationWkt,
            String thumbnailUrl,
            String contentTypeId,
            String tel,
            boolean commonSynced,
            boolean withTourSynced,
            boolean introSynced) {}
}
