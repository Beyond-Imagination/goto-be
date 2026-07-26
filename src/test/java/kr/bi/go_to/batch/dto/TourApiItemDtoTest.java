package kr.bi.go_to.batch.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TourApiItemDtoTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("현재 장소 분류체계 필드만 Tour API 항목 DTO로 역직렬화한다")
    void deserializesOnlyCurrentPlaceClassificationFields() throws Exception {
        TourApiItemDto item = jsonMapper.readValue(
                """
                {
                  "contentid": "12345",
                  "title": "Current category place",
                  "cat3": "LEGACY",
                  "lclsSystm1": "A",
                  "lclsSystm2": "A01",
                  "lclsSystm3": "A01010100"
                }
                """,
                TourApiItemDto.class);

        assertThat(item.lclsSystm1()).isEqualTo("A");
        assertThat(item.lclsSystm2()).isEqualTo("A01");
        assertThat(item.lclsSystm3()).isEqualTo("A01010100");
    }
}
