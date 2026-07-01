package kr.bi.go_to.batch.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.enums.PlaceSource;
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
}
