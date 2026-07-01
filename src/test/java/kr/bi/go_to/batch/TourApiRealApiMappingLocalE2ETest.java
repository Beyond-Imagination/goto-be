package kr.bi.go_to.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.HomepageParsingException;
import kr.bi.go_to.batch.mapper.TourApiBfDetailsNormalizer;
import kr.bi.go_to.batch.processor.TourApiBaseItemProcessor;
import kr.bi.go_to.batch.reader.TourApiBaseItemReader;
import kr.bi.go_to.batch.writer.PlaceItemWriter;
import kr.bi.go_to.enums.PlaceSource;
import kr.bi.go_to.model.place.PlaceBfDetails;
import lombok.extern.slf4j.Slf4j;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("local-test")
@EnabledIfEnvironmentVariable(named = "TOUR_API_REAL_E2E_ENABLED", matches = "true")
@Slf4j
class TourApiRealApiMappingLocalE2ETest {

    private static final int MAX_CANDIDATES = 10;
    private static final Set<String> BF_DETAILS_SCHEMA_TOP_LEVEL_KEYS =
            Set.of("mobility", "visual", "hearing", "infant_family", "intro", "sources");
    private static final Set<String> TOUR_API_RAW_BF_DETAIL_KEYS = Set.of(
            "contentid",
            "parking",
            "route",
            "publictransport",
            "ticketoffice",
            "promotion",
            "wheelchair",
            "exit",
            "elevator",
            "restroom",
            "auditorium",
            "room",
            "handicapetc",
            "braileblock",
            "helpdog",
            "guidehuman",
            "audioguide",
            "bigprint",
            "brailepromotion",
            "guidesystem",
            "blindhandicapetc",
            "signguide",
            "videoguide",
            "hearingroom",
            "hearinghandicapetc",
            "stroller",
            "lactationroom",
            "babysparechair",
            "infantsfamilyetc");

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private TourApiClient tourApiClient;

    @Autowired
    private TourApiBaseItemProcessor processor;

    @Autowired
    private PlaceItemWriter writer;

    @Autowired
    private TourApiBfDetailsNormalizer bfDetailsNormalizer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("실제 Tour API 목록 항목을 세 상세 API로 보강한 뒤 저장하면 DB 매핑과 bf_details 스키마가 일치한다")
    void realTourApiListItemCanBeDetailedProcessedAndLoaded() throws Exception {
        assertThat(environment.getProperty("goto.batch.initial-load.auto-run-enabled", Boolean.class))
                .isFalse();

        TourApiBaseItemReader reader = createRealApiBaseReader();

        Optional<DetailedCandidate> candidate = readFirstFullyDetailedCandidate(reader);

        assertThat(candidate)
                .as("areaBasedList2의 첫 %d개 후보 중 세 상세 API가 모두 조회되고 bf_details 스키마 검증 가능한 항목이 있어야 한다", MAX_CANDIDATES)
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
        assertStoredBfDetailsMatchesDataModelSchema(placeId, selected);
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
                if (result != null && hasAnyAvailableBarrierFreeDetail(result)) {
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

    private boolean hasAnyAvailableBarrierFreeDetail(PlaceProcessingResult result) {
        String normalizedBfDetails = bfDetailsNormalizer.normalize(result.bfDetails(), result.introDetails());
        try {
            PlaceBfDetails bfDetails = objectMapper.readValue(normalizedBfDetails, PlaceBfDetails.class);
            return Stream.of(
                            bfDetails.getMobility(),
                            bfDetails.getVisual(),
                            bfDetails.getHearing(),
                            bfDetails.getInfantFamily())
                    .filter(Objects::nonNull)
                    .flatMap(details -> details.values().stream())
                    .anyMatch(item -> Boolean.TRUE.equals(item.getIsAvailable()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void assertStoredBfDetailsMatchesDataModelSchema(Long placeId, DetailedCandidate selected)
            throws Exception {
        String storedBfDetailsJson = jdbcTemplate.queryForObject(
                "SELECT bf_details::text FROM place_bf_info WHERE place_id = ?", String.class, placeId);
        PlaceBfDetails storedBfDetails = objectMapper.readValue(storedBfDetailsJson, PlaceBfDetails.class);

        List<String> topLevelKeys = jdbcTemplate.queryForList(
                "SELECT jsonb_object_keys(bf_details) FROM place_bf_info WHERE place_id = ?", String.class, placeId);

        logBfDetailsSchemaMapping(selected, storedBfDetailsJson, storedBfDetails, topLevelKeys);

        assertThat(topLevelKeys).containsExactlyInAnyOrderElementsOf(BF_DETAILS_SCHEMA_TOP_LEVEL_KEYS);
        assertThat(topLevelKeys).doesNotContainAnyElementsOf(TOUR_API_RAW_BF_DETAIL_KEYS);

        assertThat(storedBfDetails.getIntro()).isNotNull();
        assertThat(storedBfDetails.getIntro())
                .containsEntry("contentid", selected.baseItem().contentid());
        assertThat(storedBfDetails.getMobility()).isNotNull();
        assertThat(storedBfDetails.getVisual()).isNotNull();
        assertThat(storedBfDetails.getHearing()).isNotNull();
        assertThat(storedBfDetails.getInfantFamily()).isNotNull();
        assertThat(storedBfDetails.getSources()).isNotNull().containsKey("tour_api");

        assertBfItemMapMatchesDataModelSchema(storedBfDetails.getMobility());
        assertBfItemMapMatchesDataModelSchema(storedBfDetails.getVisual());
        assertBfItemMapMatchesDataModelSchema(storedBfDetails.getHearing());
        assertBfItemMapMatchesDataModelSchema(storedBfDetails.getInfantFamily());

        boolean hasAnyBarrierFreeDetail = Stream.of(
                        storedBfDetails.getMobility(),
                        storedBfDetails.getVisual(),
                        storedBfDetails.getHearing(),
                        storedBfDetails.getInfantFamily())
                .filter(Objects::nonNull)
                .flatMap(details -> details.values().stream())
                .anyMatch(item -> Boolean.TRUE.equals(item.getIsAvailable()));
        assertThat(hasAnyBarrierFreeDetail).isTrue();

        assertThat(storedBfDetails.getHearing()).containsKey("signguide");
        PlaceBfDetails.BfItem unknownSignGuide = storedBfDetails.getHearing().get("signguide");
        assertThat(unknownSignGuide.getIsAvailable()).isNull();
        assertThat(unknownSignGuide.getCount()).isNull();
        assertThat(unknownSignGuide.getDetails()).isNull();

        PlaceBfDetails.SourceProvenance tourApiSource =
                storedBfDetails.getSources().get("tour_api");
        assertThat(tourApiSource.getExternalId()).isEqualTo(selected.baseItem().contentid());
        assertThat(tourApiSource.getExternalSubId()).isNull();
        assertThat(tourApiSource.getEvalInfo()).isNull();
        assertThat(tourApiSource.getSyncedAt()).isNotNull();
        assertThat(tourApiSource.getDetailWithTour())
                .isNotNull()
                .containsEntry("contentid", selected.baseItem().contentid());
        assertThat(tourApiSource.getDetailIntro())
                .isNotNull()
                .containsEntry("contentid", selected.baseItem().contentid());
    }

    private void logBfDetailsSchemaMapping(
            DetailedCandidate selected,
            String storedBfDetailsJson,
            PlaceBfDetails storedBfDetails,
            List<String> topLevelKeys) {
        log.info(
                """

                [TourApiRealApiMappingLocalE2ETest] bf_details JSON schema manual verification
                GIVEN place:
                  contentId = {}
                  contentTypeId = {}
                  title = {}
                GIVEN detailWithTour2 raw JSON:
                {}
                GIVEN detailIntro2 raw JSON:
                {}
                THEN stored place_bf_info.bf_details:
                {}
                THEN schema top-level keys:
                  actual = {}
                  allowed = {}
                THEN category field summary:
                  mobility = {}
                  visual = {}
                  hearing = {}
                  infant_family = {}
                  intro keys = {}
                """,
                selected.baseItem().contentid(),
                selected.baseItem().contenttypeid(),
                selected.baseItem().title(),
                selected.result().bfDetails(),
                selected.result().introDetails(),
                storedBfDetailsJson,
                topLevelKeys,
                BF_DETAILS_SCHEMA_TOP_LEVEL_KEYS,
                summarizeBfItemMap(storedBfDetails.getMobility()),
                summarizeBfItemMap(storedBfDetails.getVisual()),
                summarizeBfItemMap(storedBfDetails.getHearing()),
                summarizeBfItemMap(storedBfDetails.getInfantFamily()),
                storedBfDetails.getIntro() == null
                        ? List.of()
                        : storedBfDetails.getIntro().keySet());
    }

    private Map<String, String> summarizeBfItemMap(Map<String, PlaceBfDetails.BfItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        return items.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> "is_available=%s, count=%s, details=%s"
                                .formatted(
                                        entry.getValue().getIsAvailable(),
                                        entry.getValue().getCount(),
                                        entry.getValue().getDetails()),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }

    private void assertBfItemMapMatchesDataModelSchema(Map<String, PlaceBfDetails.BfItem> items) {
        if (items == null) {
            return;
        }

        assertThat(items).allSatisfy((fieldName, item) -> {
            assertThat(fieldName).isNotBlank();
            assertThat(item).isNotNull();
            assertThat(item.getIsAvailable()).isIn(true, null);
            if (Boolean.TRUE.equals(item.getIsAvailable())) {
                assertThat(item.getDetails()).isNotBlank();
            } else {
                assertThat(item.getCount()).isNull();
                assertThat(item.getDetails()).isNull();
            }
            if (item.getCount() != null) {
                assertThat(item.getCount()).isGreaterThanOrEqualTo(0);
            }
        });
    }

    private record DetailedCandidate(TourApiItemDto baseItem, PlaceProcessingResult result) {}
}
