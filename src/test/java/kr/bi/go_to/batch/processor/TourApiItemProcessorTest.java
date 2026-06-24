package kr.bi.go_to.batch.processor;

import static org.assertj.core.api.Assertions.assertThat;

import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.enums.PlaceSource;
import kr.bi.go_to.model.place.Place;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TourApiItemProcessorTest {

    private TourApiItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TourApiItemProcessor();
    }

    @Test
    @DisplayName("정상적인 DTO가 주어지면 Place 엔티티로 변환된다")
    void process_validDto_returnsPlace() throws Exception {
        // given
        TourApiItemDto dto = new TourApiItemDto();
        dto.setContentid("12345");
        dto.setTitle("Test Place");
        dto.setAddr1("Seoul");
        dto.setAddr2("Gangnam");
        dto.setMapx("127.0");
        dto.setMapy("37.0");
        dto.setFirstimage("http://image1.jpg");
        dto.setTel("02-123-4567");
        dto.setContenttypeid("12");
        dto.setCat3("A0101");

        // when
        Place result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getExternalId()).isEqualTo("12345");
        assertThat(result.getSource()).isEqualTo(PlaceSource.TOUR_API.name());
        assertThat(result.getName()).isEqualTo("Test Place");
        assertThat(result.getSanitizedAddress()).isEqualTo("Seoul, Gangnam");
        assertThat(result.getLocationPoint()).isNotNull();
        assertThat(result.getLocationPoint().getX()).isEqualTo(127.0);
        assertThat(result.getLocationPoint().getY()).isEqualTo(37.0);
        assertThat(result.getLocationPoint().getSRID()).isEqualTo(4326);
        assertThat(result.getThumbnailUrl()).isEqualTo("http://image1.jpg");
        assertThat(result.getTel()).isEqualTo("02-123-4567");
        assertThat(result.getContentTypeId()).isEqualTo("12");
        assertThat(result.getCategory()).isEqualTo("A0101");
    }

    @Test
    @DisplayName("contentid가 없으면 null을 반환한다")
    void process_emptyContentId_returnsNull() throws Exception {
        // given
        TourApiItemDto dto = new TourApiItemDto();
        dto.setContentid("");
        dto.setTitle("Test Place");

        // when
        Place result = processor.process(dto);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("title이 없으면 null을 반환한다")
    void process_emptyTitle_returnsNull() throws Exception {
        // given
        TourApiItemDto dto = new TourApiItemDto();
        dto.setContentid("12345");
        dto.setTitle("");

        // when
        Place result = processor.process(dto);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 좌표 범위면 locationPoint가 null이다")
    void process_invalidCoordinateRange_locationPointIsNull() throws Exception {
        // given
        TourApiItemDto dto = new TourApiItemDto();
        dto.setContentid("12345");
        dto.setTitle("Test Place");
        dto.setMapx("200.0"); // Invalid longitude
        dto.setMapy("100.0"); // Invalid latitude

        // when
        Place result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getLocationPoint()).isNull();
    }

    @Test
    @DisplayName("좌표 포맷 에러 시 locationPoint가 null이다")
    void process_invalidCoordinateFormat_locationPointIsNull() throws Exception {
        // given
        TourApiItemDto dto = new TourApiItemDto();
        dto.setContentid("12345");
        dto.setTitle("Test Place");
        dto.setMapx("invalid");
        dto.setMapy("invalid");

        // when
        Place result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getLocationPoint()).isNull();
    }

    @Test
    @DisplayName("firstimage가 없으면 firstimage2를 썸네일로 사용한다")
    void process_fallbackImage() throws Exception {
        // given
        TourApiItemDto dto = new TourApiItemDto();
        dto.setContentid("12345");
        dto.setTitle("Test Place");
        dto.setFirstimage("");
        dto.setFirstimage2("http://image2.jpg");

        // when
        Place result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getThumbnailUrl()).isEqualTo("http://image2.jpg");
    }

    @Test
    @DisplayName("둘 다 없으면 썸네일은 null이다")
    void process_noImage_returnsNullThumbnail() throws Exception {
        // given
        TourApiItemDto dto = new TourApiItemDto();
        dto.setContentid("12345");
        dto.setTitle("Test Place");
        dto.setFirstimage("");
        dto.setFirstimage2("   "); // blank

        // when
        Place result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getThumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("주소가 한 개만 있으면 쉼표 없이 해당 주소만 저장된다")
    void process_singleAddress() throws Exception {
        // given
        TourApiItemDto dto = new TourApiItemDto();
        dto.setContentid("12345");
        dto.setTitle("Test Place");
        dto.setAddr1("Seoul");
        dto.setAddr2("");

        // when
        Place result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getSanitizedAddress()).isEqualTo("Seoul");
    }
}
