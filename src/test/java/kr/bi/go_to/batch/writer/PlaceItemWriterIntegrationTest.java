package kr.bi.go_to.batch.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.enums.PlaceSource;
import kr.bi.go_to.model.batch.CategoryResolutionStatus;
import kr.bi.go_to.model.batch.DetailSyncStatus;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlaceItemWriterIntegrationTest {

    @Autowired
    private PlaceItemWriter writer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM place_bf_info");
        jdbcTemplate.update("DELETE FROM places");
        jdbcTemplate.update("DELETE FROM tour_api_categories");
    }

    @Test
    @DisplayName("PENDING upsert는 기존 category와 detail terminal 상태를 보존한다")
    void write_preservesTerminalStatusesWhenIncomingStateIsPending() throws Exception {
        Place terminal = Place.builder()
                .externalId("terminal-status")
                .source(PlaceSource.TOUR_API.name())
                .name("상태 보존 장소")
                .categoryResolutionStatus(CategoryResolutionStatus.RESOLVED)
                .detailCommonStatus(DetailSyncStatus.SUCCESS)
                .detailWithTourStatus(DetailSyncStatus.NOT_FOUND)
                .detailIntroStatus(DetailSyncStatus.SUCCESS)
                .detailCommonSynced(true)
                .detailWithTourSynced(false)
                .detailIntroSynced(true)
                .build();
        writer.write(new Chunk<>(List.of(new PlaceProcessingResult(terminal, null, null, true, false, true))));

        Place pending = Place.builder()
                .externalId("terminal-status")
                .source(PlaceSource.TOUR_API.name())
                .name("상태 보존 장소")
                .build();
        writer.write(new Chunk<>(List.of(new PlaceProcessingResult(pending, null, null))));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                SELECT category_resolution_status,
                       detail_common_status, detail_with_tour_status, detail_intro_status,
                       detail_common_synced, detail_with_tour_synced, detail_intro_synced
                FROM places
                WHERE external_id = ? AND source = ?
                """,
                "terminal-status",
                PlaceSource.TOUR_API.name());

        assertThat(stored)
                .containsEntry("category_resolution_status", "RESOLVED")
                .containsEntry("detail_common_status", "SUCCESS")
                .containsEntry("detail_with_tour_status", "NOT_FOUND")
                .containsEntry("detail_intro_status", "SUCCESS")
                .containsEntry("detail_common_synced", true)
                .containsEntry("detail_with_tour_synced", false)
                .containsEntry("detail_intro_synced", true);
    }

    @Test
    @DisplayName("stale terminal upsert는 먼저 저장된 detail terminal 상태와 legacy boolean을 역전하지 않는다")
    void write_preservesFirstDetailTerminalStateAgainstStaleTerminalUpsert() throws Exception {
        writeDetailStatus("success-first", DetailSyncStatus.SUCCESS);
        writeDetailStatus("success-first", DetailSyncStatus.NOT_FOUND);

        writeDetailStatus("not-found-first", DetailSyncStatus.NOT_FOUND);
        writeDetailStatus("not-found-first", DetailSyncStatus.SUCCESS);

        assertThat(detailState("success-first"))
                .containsEntry("detail_common_status", "SUCCESS")
                .containsEntry("detail_common_synced", true);
        assertThat(detailState("not-found-first"))
                .containsEntry("detail_common_status", "NOT_FOUND")
                .containsEntry("detail_common_synced", false);
    }

    @Test
    @DisplayName("category는 RESOLVED에서 하향하지 않고 NOT_FOUND에서 RESOLVED로는 복구된다")
    void write_allowsOnlyForwardCategoryTerminalTransitions() throws Exception {
        insertCategoryHierarchy();
        writeCategoryStatus("resolved-first", "LEAF", CategoryResolutionStatus.RESOLVED);
        writeCategoryStatus("resolved-first", null, CategoryResolutionStatus.NOT_FOUND);

        writeCategoryStatus("not-found-first", null, CategoryResolutionStatus.NOT_FOUND);
        writeCategoryStatus("not-found-first", "LEAF", CategoryResolutionStatus.RESOLVED);

        assertThat(categoryState("resolved-first"))
                .containsEntry("category_code", "LEAF")
                .containsEntry("category_resolution_status", "RESOLVED");
        assertThat(categoryState("not-found-first"))
                .containsEntry("category_code", "LEAF")
                .containsEntry("category_resolution_status", "RESOLVED");
    }

    @Test
    @DisplayName("Tour API 무장애 원본 JSON은 PlaceBfDetails 스키마에 맞게 구조화되어 저장된다")
    void write_normalizesTourApiBarrierFreeDetailsBeforeSaving() throws Exception {
        Place place = Place.builder()
                .externalId("1067369")
                .source(PlaceSource.TOUR_API.name())
                .name("테스트 장소")
                .detailWithTourSynced(true)
                .detailIntroSynced(true)
                .detailWithTourStatus(DetailSyncStatus.SUCCESS)
                .detailIntroStatus(DetailSyncStatus.SUCCESS)
                .build();

        String bfDetails =
                """
                {
                  "contentid": "1067369",
                  "parking": "장애인 전용 주차구역 있음(9대)_무장애 편의시설",
                  "exit": "주출입구는 턱이 없어 휠체어 접근 가능함",
                  "restroom": "장애인 전용 화장실 있음",
                  "braileblock": "점자블록 있음(주요시설 앞)",
                  "lactationroom": "수유실 있음(관리사무실)",
                  "elevator": ""
                }
                """;
        String introDetails =
                """
                {
                  "contentid": "1067369",
                  "usetime": "09:00~18:00",
                  "restdate": "매주 월요일"
                }
                """;

        writer.write(
                new Chunk<>(List.of(new PlaceProcessingResult(place, bfDetails, introDetails, false, true, true))));

        Long placeId = jdbcTemplate.queryForObject(
                "SELECT id FROM places WHERE external_id = ? AND source = ?",
                Long.class,
                "1067369",
                PlaceSource.TOUR_API.name());

        Boolean hasTopLevelParking = jdbcTemplate.queryForObject(
                "SELECT jsonb_exists(bf_details, 'parking') FROM place_bf_info WHERE place_id = ?",
                Boolean.class,
                placeId);
        String parkingAvailable = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{mobility,parking,is_available}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        String parkingCount = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{mobility,parking,count}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        String visualDetails = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{visual,braileblock,details}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        String infantFamilyDetails = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{infant_family,lactationroom,details}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        Boolean hasHearingCategory = jdbcTemplate.queryForObject(
                "SELECT jsonb_exists(bf_details, 'hearing') FROM place_bf_info WHERE place_id = ?",
                Boolean.class,
                placeId);
        String unknownHearingAvailable = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{hearing,signguide,is_available}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        String unknownHearingDetails = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{hearing,signguide,details}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        String introUseTime = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{intro,usetime}' FROM place_bf_info WHERE place_id = ?", String.class, placeId);
        String tourApiExternalId = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{sources,tour_api,externalId}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        String sourceDetailWithTourParking = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{sources,tour_api,detailWithTour,parking}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        String sourceDetailIntroUseTime = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{sources,tour_api,detailIntro,usetime}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);
        String tourApiSyncedAt = jdbcTemplate.queryForObject(
                "SELECT bf_details #>> '{sources,tour_api,syncedAt}' FROM place_bf_info WHERE place_id = ?",
                String.class,
                placeId);

        assertThat(hasTopLevelParking).isFalse();
        assertThat(parkingAvailable).isEqualTo("true");
        assertThat(parkingCount).isEqualTo("9");
        assertThat(visualDetails).isEqualTo("점자블록 있음(주요시설 앞)");
        assertThat(infantFamilyDetails).isEqualTo("수유실 있음(관리사무실)");
        assertThat(hasHearingCategory).isTrue();
        assertThat(unknownHearingAvailable).isNull();
        assertThat(unknownHearingDetails).isNull();
        assertThat(introUseTime).isEqualTo("09:00~18:00");
        assertThat(tourApiExternalId).isEqualTo("1067369");
        assertThat(sourceDetailWithTourParking).isEqualTo("장애인 전용 주차구역 있음(9대)_무장애 편의시설");
        assertThat(sourceDetailIntroUseTime).isEqualTo("09:00~18:00");
        assertThat(tourApiSyncedAt).isNotBlank();
    }

    private void writeDetailStatus(String externalId, DetailSyncStatus status) throws Exception {
        Place place = Place.builder()
                .externalId(externalId)
                .source(PlaceSource.TOUR_API.name())
                .name(externalId)
                .detailCommonStatus(status)
                .detailCommonSynced(status == DetailSyncStatus.SUCCESS)
                .build();
        writer.write(new Chunk<>(List.of(
                new PlaceProcessingResult(place, null, null, status == DetailSyncStatus.SUCCESS, false, false))));
    }

    private Map<String, Object> detailState(String externalId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT detail_common_status, detail_common_synced
                FROM places
                WHERE external_id = ? AND source = ?
                """,
                externalId,
                PlaceSource.TOUR_API.name());
    }

    private void writeCategoryStatus(
            String externalId, String categoryCode, CategoryResolutionStatus categoryResolutionStatus)
            throws Exception {
        Place place = Place.builder()
                .externalId(externalId)
                .source(PlaceSource.TOUR_API.name())
                .name(externalId)
                .categoryCode(categoryCode)
                .categoryResolutionStatus(categoryResolutionStatus)
                .build();
        writer.write(new Chunk<>(List.of(new PlaceProcessingResult(place, null, null))));
    }

    private Map<String, Object> categoryState(String externalId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT category_code, category_resolution_status
                FROM places
                WHERE external_id = ? AND source = ?
                """,
                externalId,
                PlaceSource.TOUR_API.name());
    }

    private void insertCategoryHierarchy() {
        UUID syncToken = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO tour_api_categories (code, parent_code, depth, name, last_seen_sync_token)
                VALUES ('LARGE', NULL, 1, 'large', ?),
                       ('MIDDLE', 'LARGE', 2, 'middle', ?),
                       ('LEAF', 'MIDDLE', 3, 'leaf', ?)
                """,
                syncToken,
                syncToken,
                syncToken);
    }
}
