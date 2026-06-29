package kr.bi.go_to.batch.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

@DisplayName("TourApiDetailItemReader 상세 보강 대상 조회 테스트")
class TourApiDetailItemReaderTest {

    private CapturingJdbcTemplate jdbcTemplate;
    private TourApiClient tourApiClient;
    private ThreadPoolTaskExecutor taskExecutor;
    private ItemReader<?> reader;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new CapturingJdbcTemplate();
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(1);
        taskExecutor.setMaxPoolSize(1);
        taskExecutor.initialize();

        tourApiClient = mock(TourApiClient.class);
        reader = new TourApiDetailItemReader(jdbcTemplate, tourApiClient, taskExecutor);
        ReflectionTestUtils.setField(reader, "detailQuota", 250);
    }

    @AfterEach
    void tearDown() {
        taskExecutor.shutdown();
    }

    @Test
    @DisplayName("상세 보강 대상 장소가 있으면 read로 미완료 detail API 조건의 Lazy Detail Fetch 대상을 조회한다")
    void selectsNotDeletedAndIncompleteDetailPlacesForLazyDetailEnrichment() throws Exception {
        reader.read();

        assertThat(jdbcTemplate.capturedSql).contains("source = 'TOUR_API'");
        assertThat(jdbcTemplate.capturedSql).contains("is_deleted = false");
        assertThat(jdbcTemplate.capturedSql).contains("detail_common_synced = false");
        assertThat(jdbcTemplate.capturedSql).contains("detail_with_tour_synced = false");
        assertThat(jdbcTemplate.capturedSql).contains("detail_intro_synced = false");
        assertThat(jdbcTemplate.capturedSql).doesNotContain("overview IS NULL");
        assertThat(jdbcTemplate.capturedArgs).containsExactly(250);
    }

    @Test
    @DisplayName("detailCommon2는 성공했지만 overview/homepage가 없으면 read 결과를 빈 문자열로 매핑한다")
    void mapsMissingCommonDetailFieldsToEmptyStringsWhenCommonDetailSucceeds() throws Exception {
        jdbcTemplate.returnSinglePlaceRow();
        JsonNode common2 = mock(JsonNode.class);
        JsonNode withTour2 = mock(JsonNode.class);
        JsonNode intro2 = mock(JsonNode.class);
        when(tourApiClient.fetchDetail(eq("detailCommon2"), eq("12345"), isNull()))
                .thenReturn(common2);
        when(tourApiClient.fetchDetail(eq("detailWithTour2"), eq("12345"), isNull()))
                .thenReturn(withTour2);
        when(tourApiClient.fetchDetail(eq("detailIntro2"), eq("12345"), eq("12")))
                .thenReturn(intro2);
        when(tourApiClient.extractFieldOrEmpty(common2, "overview")).thenReturn("");
        when(tourApiClient.extractFieldOrEmpty(common2, "homepage")).thenReturn("");

        TourApiItemDto dto = (TourApiItemDto) reader.read();

        assertThat(dto.overview()).isEmpty();
        assertThat(dto.homepage()).isEmpty();
        assertThat(dto.detailCommonSynced()).isTrue();
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private String capturedSql;
        private Object[] capturedArgs;
        private boolean returnSinglePlaceRow;

        private void returnSinglePlaceRow() {
            this.returnSinglePlaceRow = true;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
            this.capturedSql = sql;
            this.capturedArgs = args;
            if (!returnSinglePlaceRow) {
                return List.of();
            }

            ResultSet rs = mock(ResultSet.class);
            try {
                when(rs.getString("external_id")).thenReturn("12345");
                when(rs.getString("source")).thenReturn("TOUR_API");
                when(rs.getString("category")).thenReturn("A0101");
                when(rs.getString("name")).thenReturn("Test Place");
                when(rs.getString("sanitized_address")).thenReturn("Seoul");
                when(rs.getString("thumbnail_url")).thenReturn("https://image.example/test.jpg");
                when(rs.getString("content_type_id")).thenReturn("12");
                when(rs.getString("tel")).thenReturn("02-123-4567");
                return List.of(rowMapper.mapRow(rs, 0));
            } catch (SQLException e) {
                throw new DataRetrievalFailureException("Failed to map test row", e);
            }
        }
    }
}
