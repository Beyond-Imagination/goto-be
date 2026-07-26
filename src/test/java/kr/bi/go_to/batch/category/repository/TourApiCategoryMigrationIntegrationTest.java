package kr.bi.go_to.batch.category.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class TourApiCategoryMigrationIntegrationTest {

    @Test
    @DisplayName("V13은 Tour API 장소의 기존 현재 소분류 코드를 변환 없이 보존한다")
    void upgradePreservesPreexistingCurrentLeafCodesWithoutBackfill() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();

            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure()
                    .dataSource(dataSource)
                    .target(MigrationVersion.fromVersion("12"))
                    .load()
                    .migrate();

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update(
                    "INSERT INTO places (external_id, source, category, name) VALUES (?, ?, ?, ?)",
                    "already-migrated",
                    "TOUR_API",
                    "A01010100",
                    "existing place");

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM places", Long.class))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT category_code FROM places WHERE external_id = 'already-migrated'", String.class))
                    .isEqualTo("A01010100");

            jdbcTemplate.update(
                    """
                    INSERT INTO tour_api_categories
                        (code, parent_code, depth, name, active, last_seen_sync_token)
                    VALUES
                        ('LARGE', NULL, 1, 'large', TRUE, ?::uuid),
                        ('MIDDLE', 'LARGE', 2, 'middle', TRUE, ?::uuid),
                        ('A01010100', 'MIDDLE', 3, 'small', TRUE, ?::uuid)
                    """,
                    "00000000-0000-0000-0000-000000000001",
                    "00000000-0000-0000-0000-000000000001",
                    "00000000-0000-0000-0000-000000000001");
            jdbcTemplate.execute("ALTER TABLE places VALIDATE CONSTRAINT fk_places_tour_api_category");

            assertThat(jdbcTemplate.queryForObject(
                            """
                            SELECT convalidated
                            FROM pg_constraint
                            WHERE conname = 'fk_places_tour_api_category'
                            """,
                            Boolean.class))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("V13은 non-Tour 장소에 non-null 분류가 있으면 rename 전에 트랜잭션으로 차단한다")
    void upgradeBlocksNonTourNonNullCategoryBeforeRename() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();

            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure()
                    .dataSource(dataSource)
                    .target(MigrationVersion.fromVersion("12"))
                    .load()
                    .migrate();

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update(
                    "INSERT INTO places (external_id, source, category, name) VALUES (?, ?, ?, ?)",
                    "mixed-source",
                    "USER",
                    "A01010100",
                    "mixed source place");

            assertThatThrownBy(() ->
                            Flyway.configure().dataSource(dataSource).load().migrate())
                    .hasMessageContaining("V13")
                    .rootCause()
                    .hasMessageContaining("non-Tour");

            assertThat(jdbcTemplate.queryForObject(
                            """
                            SELECT EXISTS (
                                SELECT 1
                                FROM information_schema.columns
                                WHERE table_name = 'places' AND column_name = 'category'
                            )
                            """,
                            Boolean.class))
                    .isTrue();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT category FROM places WHERE external_id = 'mixed-source'", String.class))
                    .isEqualTo("A01010100");
        }
    }
}
