package kr.bi.go_to.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local-test")
@ConditionalOnProperty(prefix = "goto.local-test.seed", name = "enabled", havingValue = "true")
public class LocalTestDataSeedRunner implements ApplicationRunner {

    private static final String CREATE_SEED_HISTORY_TABLE =
            """
            CREATE TABLE IF NOT EXISTS local_test_seed_history (
                id BIGSERIAL PRIMARY KEY,
                script_location TEXT NOT NULL,
                script_checksum TEXT NOT NULL,
                seeded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (script_location, script_checksum)
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ResourceLoader resourceLoader;
    private final DataSource dataSource;
    private final String scriptLocation;

    public LocalTestDataSeedRunner(
            JdbcTemplate jdbcTemplate,
            ResourceLoader resourceLoader,
            DataSource dataSource,
            @Value("${goto.local-test.seed.script-location:file:src/test/resources/db/seed/mock_test_data.sql}")
                    String scriptLocation) {
        this.jdbcTemplate = jdbcTemplate;
        this.resourceLoader = resourceLoader;
        this.dataSource = dataSource;
        this.scriptLocation = scriptLocation;
    }

    @Override
    public void run(ApplicationArguments args) {
        Resource seedScript = resourceLoader.getResource(scriptLocation);
        if (!seedScript.exists()) {
            log.warn("Local test seed script not found. Skipping seed. location={}", scriptLocation);
            return;
        }

        String scriptChecksum = calculateChecksum(seedScript);
        jdbcTemplate.execute(CREATE_SEED_HISTORY_TABLE);
        if (hasAlreadySeeded(scriptChecksum)) {
            log.info(
                    "Local test seed script already applied. Skipping seed. location={}, checksum={}",
                    scriptLocation,
                    scriptChecksum);
            return;
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(seedScript);
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, dataSource);

        jdbcTemplate.update(
                "INSERT INTO local_test_seed_history (script_location, script_checksum) VALUES (?, ?)",
                scriptLocation,
                scriptChecksum);
        log.info("Local test seed script applied. location={}, checksum={}", scriptLocation, scriptChecksum);
    }

    private boolean hasAlreadySeeded(String scriptChecksum) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM local_test_seed_history WHERE script_location = ? AND script_checksum = ?)",
                Boolean.class,
                scriptLocation,
                scriptChecksum);
        return Boolean.TRUE.equals(exists);
    }

    private String calculateChecksum(Resource seedScript) {
        try (InputStream inputStream = seedScript.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read local test seed script: " + scriptLocation, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
