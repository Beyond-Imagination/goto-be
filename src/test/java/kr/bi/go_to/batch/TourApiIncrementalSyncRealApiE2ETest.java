package kr.bi.go_to.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.reader.TourApiIncrementalItemReader;
import kr.bi.go_to.batch.support.TourApiIncrementalSyncContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

@SpringBootTest
@ActiveProfiles("local-test")
@EnabledIfEnvironmentVariable(named = "TOUR_API_REAL_E2E_ENABLED", matches = "true")
class TourApiIncrementalSyncRealApiE2ETest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TOUR_API_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("실제 areaBasedSyncList2 API를 modifiedtime 기준으로 조회하면 resultCode=0000 응답을 파싱할 수 있다")
    void realAreaBasedSyncList2ReturnsParsableItems() throws Exception {
        assertThat(environment.getProperty("goto.batch.initial-load.auto-run-enabled", Boolean.class))
                .isFalse();

        TourApiIncrementalItemReader reader = createRealApiIncrementalReader();

        TourApiItemDto item = reader.read();

        if (item != null) {
            assertThat(item.contentid()).isNotBlank();
            assertThat(item.showflag()).isNotBlank();
        }
    }

    private TourApiIncrementalItemReader createRealApiIncrementalReader() {
        TourApiIncrementalItemReader reader = new TourApiIncrementalItemReader(restClientBuilder, jdbcTemplate);
        ReflectionTestUtils.setField(reader, "serviceKey", requiredProperty("tour-api.service-key"));
        ReflectionTestUtils.setField(reader, "baseUrl", requiredProperty("tour-api.base-url"));
        ReflectionTestUtils.setField(reader, "mobileOs", requiredProperty("tour-api.mobile-os"));
        ReflectionTestUtils.setField(reader, "mobileApp", requiredProperty("tour-api.mobile-app"));
        ReflectionTestUtils.setField(reader, "clock", Clock.system(KST));

        String requestDate = findRequestDateForE2E();
        ReflectionTestUtils.setField(reader, "requestDate", requestDate);
        ReflectionTestUtils.setField(reader, "targetDate", LocalDate.now(KST).format(TOUR_API_DATE_FORMAT));
        ReflectionTestUtils.setField(reader, "isInitialized", true);
        return reader;
    }

    private String findRequestDateForE2E() {
        String sql =
                "SELECT target_date FROM batch_sync_log WHERE status = 'SUCCESS' AND job_name = ? ORDER BY created_at DESC LIMIT 1";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, TourApiIncrementalSyncContext.JOB_NAME);
        } catch (EmptyResultDataAccessException e) {
            return LocalDate.now(KST).minusDays(1).format(TOUR_API_DATE_FORMAT);
        }
    }

    private String requiredProperty(String key) {
        String value = environment.getProperty(key);
        assertThat(value)
                .as("%s property must be configured for real Tour API local E2E test", key)
                .isNotBlank();
        return value;
    }
}
