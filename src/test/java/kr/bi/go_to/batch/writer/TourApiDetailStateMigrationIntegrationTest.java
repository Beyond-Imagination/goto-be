package kr.bi.go_to.batch.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class TourApiDetailStateMigrationIntegrationTest {

    @Test
    void migrationBackfillsPersistedPayloadsAndRetriesUnrecoverableLegacySuccess() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();

            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure()
                    .dataSource(dataSource)
                    .target(MigrationVersion.fromVersion("14"))
                    .load()
                    .migrate();

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            Long recoverableId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO places (
                        external_id, source, name,
                        detail_common_synced, detail_with_tour_synced, detail_intro_synced
                    )
                    VALUES ('recoverable', 'TOUR_API', 'recoverable', true, true, true)
                    RETURNING id
                    """,
                    Long.class);
            jdbcTemplate.update(
                    """
                    INSERT INTO place_bf_info (place_id, bf_details, last_synced_at)
                    VALUES (?, ?::jsonb, NOW())
                    """,
                    recoverableId,
                    """
                    {
                      "sources": {
                        "tour_api": {
                          "detailWithTour": {"parking": "전용 주차"},
                          "detailIntro": {"usetime": "09:00"}
                        }
                      }
                    }
                    """);
            jdbcTemplate.update(
                    """
                    INSERT INTO places (
                        external_id, source, name,
                        detail_common_synced, detail_with_tour_synced, detail_intro_synced
                    )
                    VALUES ('unrecoverable', 'TOUR_API', 'unrecoverable', true, true, false)
                    """);

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(
                            jdbcTemplate.queryForMap(
                                    """
                            SELECT detail_with_tour_status, detail_intro_status,
                                   detail_with_tour_synced, detail_intro_synced,
                                   detail_with_tour_payload #>> '{parking}' AS parking,
                                   detail_intro_payload #>> '{usetime}' AS usetime
                            FROM places
                            WHERE external_id = 'recoverable'
                            """))
                    .containsAllEntriesOf(Map.of(
                            "detail_with_tour_status", "SUCCESS",
                            "detail_intro_status", "SUCCESS",
                            "detail_with_tour_synced", true,
                            "detail_intro_synced", true,
                            "parking", "전용 주차",
                            "usetime", "09:00"));
            assertThat(
                            jdbcTemplate.queryForMap(
                                    """
                            SELECT detail_with_tour_status, detail_intro_status,
                                   detail_with_tour_synced, detail_intro_synced
                            FROM places
                            WHERE external_id = 'unrecoverable'
                            """))
                    .containsAllEntriesOf(Map.of(
                            "detail_with_tour_status",
                            "PENDING",
                            "detail_intro_status",
                            "PENDING",
                            "detail_with_tour_synced",
                            false,
                            "detail_intro_synced",
                            false));
        }
    }
}
