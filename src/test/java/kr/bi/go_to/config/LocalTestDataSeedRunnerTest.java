package kr.bi.go_to.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

class LocalTestDataSeedRunnerTest {

    private static final String SEED_SQL_CHECKSUM = "21d302b9471eea1e1df4250282953b6d62d466ce95fe28d5107c888166bb19f6";

    @Test
    @DisplayName("seed SQL 파일이 아직 제공되지 않았으면 로컬 seed를 건너뛴다")
    void skipsSeedWhenScriptDoesNotExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        DataSource dataSource = mock(DataSource.class);
        Resource resource = mock(Resource.class);

        when(resourceLoader.getResource("file:seed.sql")).thenReturn(resource);
        when(resource.exists()).thenReturn(false);

        LocalTestDataSeedRunner runner =
                new LocalTestDataSeedRunner(jdbcTemplate, resourceLoader, dataSource, "file:seed.sql");

        runner.run(new DefaultApplicationArguments(new String[0]));

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("이미 적용된 seed SQL checksum은 같은 로컬 DB에 다시 실행하지 않는다")
    void skipsSeedWhenScriptWasAlreadyApplied() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        DataSource dataSource = mock(DataSource.class);
        Resource resource = mock(Resource.class);

        when(resourceLoader.getResource("file:seed.sql")).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getInputStream())
                .thenReturn(new ByteArrayInputStream("seed sql".getBytes(StandardCharsets.UTF_8)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("file:seed.sql"), eq(SEED_SQL_CHECKSUM)))
                .thenReturn(true);

        LocalTestDataSeedRunner runner =
                new LocalTestDataSeedRunner(jdbcTemplate, resourceLoader, dataSource, "file:seed.sql");

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute(anyString());
        verify(jdbcTemplate).queryForObject(anyString(), eq(Boolean.class), eq("file:seed.sql"), eq(SEED_SQL_CHECKSUM));
        verifyNoInteractions(dataSource);
    }
}
