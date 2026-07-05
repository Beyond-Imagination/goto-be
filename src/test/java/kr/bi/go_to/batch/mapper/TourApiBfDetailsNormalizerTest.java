package kr.bi.go_to.batch.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.bi.go_to.model.place.PlaceBfDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TourApiBfDetailsNormalizerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T00:00:00Z");

    private final ObjectMapper objectMapper =
            JsonMapper.builder().findAndAddModules().build();
    private final TourApiBfDetailsNormalizer normalizer =
            new TourApiBfDetailsNormalizer(objectMapper, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("원본 JSON에 contentid가 없어도 전달받은 externalId를 provenance에 저장한다")
    void normalize_usesExternalIdParameterForSourceProvenance() throws Exception {
        String bfDetailsJson =
                """
                {
                  "parking": "장애인 전용 주차구역 있음(9대)_무장애 편의시설"
                }
                """;
        String introDetailsJson =
                """
                {
                  "usetime": "09:00~18:00"
                }
                """;

        String normalized = normalizer.normalize(" 1067369 ", bfDetailsJson, introDetailsJson);

        PlaceBfDetails bfDetails = objectMapper.readValue(normalized, PlaceBfDetails.class);
        PlaceBfDetails.SourceProvenance tourApiSource = bfDetails.getSources().get("tour_api");
        assertThat(tourApiSource.getExternalId()).isEqualTo("1067369");
        assertThat(tourApiSource.getSyncedAt()).isEqualTo(FIXED_NOW);
        assertThat(tourApiSource.getDetailWithTour()).doesNotContainKey("contentid");
        assertThat(tourApiSource.getDetailIntro()).doesNotContainKey("contentid");
        assertThat(bfDetails.getMobility().get("parking").getCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("externalId가 비어 있으면 정규화를 중단한다")
    void normalize_rejectsBlankExternalId() {
        assertThatThrownBy(() -> normalizer.normalize(" ", "{}", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalId is required");
    }
}
