package kr.bi.go_to.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.HomepageParsingException;
import kr.bi.go_to.batch.processor.TourApiBaseItemProcessor;
import kr.bi.go_to.batch.reader.TourApiBaseItemReader;
import kr.bi.go_to.batch.writer.PlaceItemWriter;
import kr.bi.go_to.enums.PlaceSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@ActiveProfiles("local-test")
@EnabledIfEnvironmentVariable(named = "TOUR_API_REAL_E2E_ENABLED", matches = "true")
class TourApiRealApiMappingLocalE2ETest {

    private static final int MAX_CANDIDATES = 10;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private TourApiClient tourApiClient;

    @Autowired
    private TourApiBaseItemProcessor processor;

    @Autowired
    private PlaceItemWriter writer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("실제 Tour API 목록 항목을 세 상세 API로 보강한 뒤 processor와 writer를 돌리면 DB 매핑이 일치한다")
    void realTourApiListItemCanBeDetailedProcessedAndLoaded() throws Exception {
        assertThat(environment.getProperty("goto.batch.initial-load.auto-run-enabled", Boolean.class))
                .isFalse();

        TourApiBaseItemReader reader = createRealApiBaseReader();

        Optional<DetailedCandidate> candidate = readFirstFullyDetailedCandidate(reader);

        assertThat(candidate)
                .as(
                        "areaBasedList2의 첫 %d개 후보 중 detailCommon2/detailWithTour2/detailIntro2가 모두 조회되고 processor를 통과하는 항목이 있어야 한다",
                        MAX_CANDIDATES)
                .isPresent();

        DetailedCandidate selected = candidate.orElseThrow();
        PlaceProcessingResult result = selected.result();

        assertThat(result.detailCommonSynced()).isTrue();
        assertThat(result.detailWithTourSynced()).isTrue();
        assertThat(result.detailIntroSynced()).isTrue();

        writer.write(new Chunk<>(List.of(result)));

        Map<String, Object> placeRow = jdbcTemplate.queryForMap(
                """
                SELECT id, name, content_type_id, overview, homepage, is_deleted,
                       detail_common_synced, detail_with_tour_synced, detail_intro_synced
                FROM places
                WHERE external_id = ? AND source = ?
                """,
                selected.baseItem().contentid(),
                PlaceSource.TOUR_API.name());

        assertThat(placeRow.get("name")).isEqualTo(selected.baseItem().title());
        assertThat(placeRow.get("content_type_id"))
                .isEqualTo(selected.baseItem().contenttypeid());
        assertThat(placeRow.get("overview")).isEqualTo(result.place().getOverview());
        assertThat(placeRow.get("homepage")).isEqualTo(result.place().getHomepage());
        assertThat(placeRow.get("is_deleted")).isEqualTo(false);
        assertThat(placeRow.get("detail_common_synced")).isEqualTo(true);
        assertThat(placeRow.get("detail_with_tour_synced")).isEqualTo(true);
        assertThat(placeRow.get("detail_intro_synced")).isEqualTo(true);

        Long placeId = ((Number) placeRow.get("id")).longValue();
        Map<String, Object> bfInfoRow = jdbcTemplate.queryForMap(
                """
                SELECT (bf_details - 'intro') = CAST(? AS jsonb) AS bf_details_matches,
                       bf_details -> 'intro' = CAST(? AS jsonb) AS intro_details_matches
                FROM place_bf_info
                WHERE place_id = ?
                """,
                result.bfDetails(),
                result.introDetails(),
                placeId);

        assertThat(bfInfoRow.get("bf_details_matches")).isEqualTo(true);
        assertThat(bfInfoRow.get("intro_details_matches")).isEqualTo(true);
    }

    private TourApiBaseItemReader createRealApiBaseReader() {
        TourApiBaseItemReader reader = new TourApiBaseItemReader(restClientBuilder);
        ReflectionTestUtils.setField(reader, "serviceKey", requiredProperty("tour-api.service-key"));
        ReflectionTestUtils.setField(reader, "baseUrl", requiredProperty("tour-api.base-url"));
        ReflectionTestUtils.setField(reader, "mobileOs", requiredProperty("tour-api.mobile-os"));
        ReflectionTestUtils.setField(reader, "mobileApp", requiredProperty("tour-api.mobile-app"));
        return reader;
    }

    private String requiredProperty(String key) {
        String value = environment.getProperty(key);
        assertThat(value)
                .as("%s property must be configured for real Tour API local E2E test", key)
                .isNotBlank();
        return value;
    }

    private Optional<DetailedCandidate> readFirstFullyDetailedCandidate(TourApiBaseItemReader reader) throws Exception {
        for (int i = 0; i < MAX_CANDIDATES; i++) {
            TourApiItemDto baseItem = reader.read();
            if (baseItem == null) {
                return Optional.empty();
            }

            if (!isProcessableBaseItem(baseItem)) {
                continue;
            }

            Optional<TourApiItemDto> detailedItem = fetchAllDetails(baseItem);
            if (detailedItem.isEmpty()) {
                continue;
            }

            try {
                PlaceProcessingResult result = processor.process(detailedItem.get());
                if (result != null) {
                    return Optional.of(new DetailedCandidate(baseItem, result));
                }
            } catch (HomepageParsingException ignored) {
                // 실제 API 데이터 중 홈페이지 포맷이 정제 불가능한 항목은 이 매핑 검증의 후보에서 제외한다.
            }
        }
        return Optional.empty();
    }

    private boolean isProcessableBaseItem(TourApiItemDto baseItem) {
        return StringUtils.hasText(baseItem.contentid()) && StringUtils.hasText(baseItem.title());
    }

    private Optional<TourApiItemDto> fetchAllDetails(TourApiItemDto baseItem) {
        JsonNode commonDetails =
                tourApiClient.fetchDetail("detailCommon2", baseItem.contentid(), baseItem.contenttypeid());
        JsonNode bfDetails =
                tourApiClient.fetchDetail("detailWithTour2", baseItem.contentid(), baseItem.contenttypeid());
        JsonNode introDetails =
                tourApiClient.fetchDetail("detailIntro2", baseItem.contentid(), baseItem.contenttypeid());

        if (commonDetails == null || bfDetails == null || introDetails == null) {
            return Optional.empty();
        }

        return Optional.of(baseItem.withDetails(
                tourApiClient.extractFieldOrEmpty(commonDetails, "overview"),
                tourApiClient.extractFieldOrEmpty(commonDetails, "homepage"),
                bfDetails.toString(),
                introDetails.toString(),
                true,
                true,
                true));
    }

    private record DetailedCandidate(TourApiItemDto baseItem, PlaceProcessingResult result) {}
}
