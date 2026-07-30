package kr.bi.go_to.batch.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryException;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryReason;
import kr.bi.go_to.batch.exception.TourApiInfrastructureException;
import kr.bi.go_to.batch.validation.TourApiPlaceCategoryValidator;
import kr.bi.go_to.model.batch.DetailSyncStatus;
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
    private TourApiPlaceCategoryValidator categoryValidator;
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
        categoryValidator = mock(TourApiPlaceCategoryValidator.class);
        reader = new TourApiDetailItemReader(jdbcTemplate, tourApiClient, categoryValidator, taskExecutor);
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

        assertThat(jdbcTemplate.capturedSql).contains("category_code");
        assertThat(jdbcTemplate.capturedSql).doesNotContain(" category,");
        assertThat(jdbcTemplate.capturedSql).contains("source = 'TOUR_API'");
        assertThat(jdbcTemplate.capturedSql).contains("is_deleted = false");
        assertThat(jdbcTemplate.capturedSql).contains("detail_common_status = ?");
        assertThat(jdbcTemplate.capturedSql).contains("detail_with_tour_status = ?");
        assertThat(jdbcTemplate.capturedSql).contains("detail_intro_status = ?");
        assertThat(jdbcTemplate.capturedSql).contains("ORDER BY updated_at ASC, id ASC");
        assertThat(jdbcTemplate.capturedSql).doesNotContain("overview IS NULL");
        assertThat(jdbcTemplate.capturedArgs).containsExactly("PENDING", "PENDING", "PENDING", "PENDING", 250);
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

        assertThat(dto.lclsSystm1()).isNull();
        assertThat(dto.lclsSystm2()).isNull();
        assertThat(dto.lclsSystm3()).isEqualTo("A0101");
        assertThat(dto.overview()).isEmpty();
        assertThat(dto.homepage()).isEmpty();
        assertThat(dto.detailCommonSynced()).isTrue();
    }

    @Test
    @DisplayName("한 endpoint의 인프라 장애는 해당 상태만 PENDING으로 남기고 나머지 endpoint를 계속 처리한다")
    void isolatesInfrastructureFailureToTheFailingEndpoint() throws Exception {
        jdbcTemplate.returnSinglePlaceRow();
        TourApiInfrastructureException failure = new TourApiInfrastructureException("provider unavailable");
        when(tourApiClient.fetchDetail(eq("detailCommon2"), eq("12345"), isNull()))
                .thenThrow(failure);
        when(tourApiClient.fetchDetail(eq("detailWithTour2"), eq("12345"), isNull()))
                .thenReturn(mock(JsonNode.class));
        when(tourApiClient.fetchDetail(eq("detailIntro2"), eq("12345"), eq("12")))
                .thenReturn(null);

        TourApiItemDto dto = (TourApiItemDto) reader.read();

        assertThat(dto.detailCommonStatus()).isEqualTo(DetailSyncStatus.PENDING);
        assertThat(dto.detailWithTourStatus()).isEqualTo(DetailSyncStatus.SUCCESS);
        assertThat(dto.detailIntroStatus()).isEqualTo(DetailSyncStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("이미 종결된 endpoint는 호출하지 않고 PENDING endpoint만 호출한다")
    void callsOnlyPendingEndpoints() throws Exception {
        jdbcTemplate.returnSinglePlaceRow();
        jdbcTemplate.detailCommonStatus = "SUCCESS";
        jdbcTemplate.detailWithTourStatus = "NOT_FOUND";
        jdbcTemplate.detailIntroStatus = "PENDING";
        when(tourApiClient.fetchDetail(eq("detailIntro2"), eq("12345"), eq("12")))
                .thenReturn(null);

        TourApiItemDto dto = (TourApiItemDto) reader.read();

        verify(tourApiClient, never()).fetchDetail(eq("detailCommon2"), eq("12345"), isNull());
        verify(tourApiClient, never()).fetchDetail(eq("detailWithTour2"), eq("12345"), isNull());
        assertThat(dto.detailIntroStatus()).isEqualTo(DetailSyncStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("빈 category는 detailCommon2의 최신 lclsSystm3로 한 번 복구한다")
    void resolvesMissingCategoryFromDetailCommon() throws Exception {
        jdbcTemplate.returnSinglePlaceRow();
        jdbcTemplate.categoryCode = "";
        jdbcTemplate.categoryResolutionStatus = "PENDING";
        JsonNode common = mock(JsonNode.class);
        when(tourApiClient.fetchDetail(eq("detailCommon2"), eq("12345"), isNull()))
                .thenReturn(common);
        when(tourApiClient.extractFieldOrEmpty(common, "lclsSystm3")).thenReturn("A01010100");
        when(categoryValidator.requireActiveLeaf("12345", "A01010100")).thenReturn("A01010100");
        when(tourApiClient.fetchDetail(eq("detailWithTour2"), eq("12345"), isNull()))
                .thenReturn(null);
        when(tourApiClient.fetchDetail(eq("detailIntro2"), eq("12345"), eq("12")))
                .thenReturn(null);

        TourApiItemDto dto = (TourApiItemDto) reader.read();

        assertThat(dto.lclsSystm3()).isEqualTo("A01010100");
        assertThat(dto.categoryResolutionStatus().name()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("복구된 category가 활성 leaf가 아니면 terminal 처리하고 dependent detail API를 호출하지 않는다")
    void terminatesInvalidRecoveredCategoryBeforeDependentDetailCalls() throws Exception {
        jdbcTemplate.returnSinglePlaceRow();
        jdbcTemplate.categoryCode = "";
        jdbcTemplate.categoryResolutionStatus = "PENDING";
        JsonNode common = mock(JsonNode.class);
        when(tourApiClient.fetchDetail(eq("detailCommon2"), eq("12345"), isNull()))
                .thenReturn(common);
        when(tourApiClient.extractFieldOrEmpty(common, "lclsSystm3")).thenReturn("UNKNOWN");
        when(categoryValidator.requireActiveLeaf("12345", "UNKNOWN"))
                .thenThrow(new InvalidTourApiCategoryException(
                        InvalidTourApiCategoryReason.UNKNOWN_INACTIVE_OR_NON_LEAF, "12345", "UNKNOWN"));

        TourApiItemDto dto = (TourApiItemDto) reader.read();

        assertThat(dto.lclsSystm3()).isNull();
        assertThat(dto.categoryResolutionStatus().name()).isEqualTo("NOT_FOUND");
        assertThat(dto.detailWithTourStatus()).isEqualTo(DetailSyncStatus.SKIPPED);
        assertThat(dto.detailIntroStatus()).isEqualTo(DetailSyncStatus.SKIPPED);
        verify(tourApiClient, never()).fetchDetail(eq("detailWithTour2"), eq("12345"), isNull());
        verify(tourApiClient, never()).fetchDetail(eq("detailIntro2"), eq("12345"), eq("12"));
    }

    @Test
    @DisplayName("category 복구가 NOT_FOUND여도 기존 detail terminal 상태를 SKIPPED로 하향하지 않는다")
    void preservesTerminalDetailStatesWhenCategoryRecoveryReturnsNotFound() throws Exception {
        jdbcTemplate.returnSinglePlaceRow();
        jdbcTemplate.categoryCode = "";
        jdbcTemplate.categoryResolutionStatus = "PENDING";
        jdbcTemplate.detailCommonStatus = "SUCCESS";
        jdbcTemplate.detailWithTourStatus = "NOT_FOUND";
        jdbcTemplate.detailIntroStatus = "SUCCESS";
        when(tourApiClient.fetchDetail(eq("detailCommon2"), eq("12345"), isNull()))
                .thenReturn(null);

        TourApiItemDto dto = (TourApiItemDto) reader.read();

        assertThat(dto.categoryResolutionStatus().name()).isEqualTo("NOT_FOUND");
        assertThat(dto.detailCommonStatus()).isEqualTo(DetailSyncStatus.SUCCESS);
        assertThat(dto.detailWithTourStatus()).isEqualTo(DetailSyncStatus.NOT_FOUND);
        assertThat(dto.detailIntroStatus()).isEqualTo(DetailSyncStatus.SUCCESS);
        assertThat(dto.detailCommonSynced()).isTrue();
        assertThat(dto.detailIntroSynced()).isTrue();
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private String capturedSql;
        private Object[] capturedArgs;
        private boolean returnSinglePlaceRow;
        private String detailCommonStatus = "PENDING";
        private String detailWithTourStatus = "PENDING";
        private String detailIntroStatus = "PENDING";
        private String categoryCode = "A0101";
        private String categoryResolutionStatus = "RESOLVED";

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
                when(rs.getString("category_code")).thenReturn(categoryCode);
                when(rs.getString("name")).thenReturn("Test Place");
                when(rs.getString("sanitized_address")).thenReturn("Seoul");
                when(rs.getString("thumbnail_url")).thenReturn("https://image.example/test.jpg");
                when(rs.getString("content_type_id")).thenReturn("12");
                when(rs.getString("tel")).thenReturn("02-123-4567");
                when(rs.getString("category_resolution_status")).thenReturn(categoryResolutionStatus);
                when(rs.getString("detail_common_status")).thenReturn(detailCommonStatus);
                when(rs.getString("detail_with_tour_status")).thenReturn(detailWithTourStatus);
                when(rs.getString("detail_intro_status")).thenReturn(detailIntroStatus);
                return List.of(rowMapper.mapRow(rs, 0));
            } catch (SQLException e) {
                throw new DataRetrievalFailureException("Failed to map test row", e);
            }
        }
    }
}
