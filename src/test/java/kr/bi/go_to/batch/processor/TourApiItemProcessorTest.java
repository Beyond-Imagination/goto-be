package kr.bi.go_to.batch.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.bi.go_to.batch.dto.PlaceProcessingResult;
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

    private TourApiItemDto createDto(
            String contentid,
            String title,
            String addr1,
            String addr2,
            String mapx,
            String mapy,
            String firstimage,
            String firstimage2,
            String tel,
            String contenttypeid,
            String cat3) {
        return new TourApiItemDto(
                contentid,
                contenttypeid,
                title,
                addr1,
                addr2,
                mapx,
                mapy,
                null, // cat1
                null, // cat2
                cat3,
                firstimage,
                firstimage2,
                null, // areacode
                null, // sigungucode
                tel,
                null, // zipcode
                null, // modifiedtime
                null, // overview
                null, // homepage
                null, // bfDetails
                null // introDetails
                );
    }

    @Test
    @DisplayName("정상적인 DTO가 주어지면 Place 엔티티로 변환된다")
    void process_validDto_returnsPlace() throws Exception {
        // given
        TourApiItemDto dto = createDto(
                "12345",
                "Test Place",
                "Seoul",
                "Gangnam",
                "127.0",
                "37.0",
                "http://image1.jpg",
                "http://image2.jpg",
                "02-123-4567",
                "12",
                "A0101");

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        Place place = result.place();
        assertThat(place).isNotNull();
        assertThat(place.getExternalId()).isEqualTo("12345");
        assertThat(place.getSource()).isEqualTo(PlaceSource.TOUR_API.name());
        assertThat(place.getName()).isEqualTo("Test Place");
        assertThat(place.getSanitizedAddress()).isEqualTo("Seoul, Gangnam");
        assertThat(place.getLocationPoint()).isNotNull();
        assertThat(place.getLocationPoint().getX()).isEqualTo(127.0);
        assertThat(place.getLocationPoint().getY()).isEqualTo(37.0);
        assertThat(place.getLocationPoint().getSRID()).isEqualTo(4326);
        assertThat(place.getThumbnailUrl()).isEqualTo("http://image1.jpg");
        assertThat(place.getTel()).isEqualTo("02-123-4567");
        assertThat(place.getContentTypeId()).isEqualTo("12");
        assertThat(place.getCategory()).isEqualTo("A0101");
    }

    @Test
    @DisplayName("contentid가 없으면 null을 반환한다")
    void process_emptyContentId_returnsNull() throws Exception {
        // given
        TourApiItemDto dto = createDto("", "Test Place", null, null, null, null, null, null, null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("title이 없으면 null을 반환한다")
    void process_emptyTitle_returnsNull() throws Exception {
        // given
        TourApiItemDto dto = createDto("12345", "", null, null, null, null, null, null, null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 좌표 범위면 locationPoint가 null이다")
    void process_invalidCoordinateRange_locationPointIsNull() throws Exception {
        // given
        TourApiItemDto dto =
                createDto("12345", "Test Place", null, null, "200.0", "100.0", null, null, null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        Place place = result.place();
        assertThat(place).isNotNull();
        assertThat(place.getLocationPoint()).isNull();
    }

    @Test
    @DisplayName("좌표 포맷 에러 시 locationPoint가 null이다")
    void process_invalidCoordinateFormat_locationPointIsNull() throws Exception {
        // given
        TourApiItemDto dto =
                createDto("12345", "Test Place", null, null, "invalid", "invalid", null, null, null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        Place place = result.place();
        assertThat(place).isNotNull();
        assertThat(place.getLocationPoint()).isNull();
    }

    @Test
    @DisplayName("firstimage가 없으면 firstimage2를 썸네일로 사용한다")
    void process_fallbackImage() throws Exception {
        // given
        TourApiItemDto dto =
                createDto("12345", "Test Place", null, null, null, null, "", "http://image2.jpg", null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        Place place = result.place();
        assertThat(place).isNotNull();
        assertThat(place.getThumbnailUrl()).isEqualTo("http://image2.jpg");
    }

    @Test
    @DisplayName("둘 다 없으면 썸네일은 null이다")
    void process_noImage_returnsNullThumbnail() throws Exception {
        // given
        TourApiItemDto dto = createDto("12345", "Test Place", null, null, null, null, "", "   ", null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        Place place = result.place();
        assertThat(place).isNotNull();
        assertThat(place.getThumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("주소가 한 개만 있으면 쉼표 없이 해당 주소만 저장된다")
    void process_singleAddress() throws Exception {
        // given
        TourApiItemDto dto = createDto("12345", "Test Place", "Seoul", "", null, null, null, null, null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNotNull();
        Place place = result.place();
        assertThat(place).isNotNull();
        assertThat(place.getSanitizedAddress()).isEqualTo("Seoul");
    }

    private TourApiItemDto createDtoWithHomepage(String homepage) {
        return new TourApiItemDto(
                "12345",
                "12",
                "Test Place",
                "Seoul",
                "Gangnam",
                "127.0",
                "37.0",
                null, // cat1
                null, // cat2
                "A0101",
                null, // firstimage
                null, // firstimage2
                null, // areacode
                null, // sigungucode
                null, // tel
                null, // zipcode
                null, // modifiedtime
                null, // overview
                homepage,
                null, // bfDetails
                null // introDetails
                );
    }

    @Test
    @DisplayName("homepage가 일반 URL이면 그대로 반환한다")
    void process_homepagePlainUrl_returnsPlainUrl() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage("https://blog.naver.com/kktm2021");
        PlaceProcessingResult result = processor.process(dto);
        assertThat(result.place().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    @DisplayName("homepage가 HTML a 태그 형식이면 URL만 추출하여 반환한다")
    void process_homepageHtmlTag_returnsUrl() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage(
                "<a href=\"https://blog.naver.com/kktm2021\" target=\"_blank\" title=\"새창\">https://blog.naver.com/kktm2021</a>");
        PlaceProcessingResult result = processor.process(dto);
        assertThat(result.place().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    @DisplayName("homepage가 HTML 엔티티를 포함한 a 태그 형식이면 URL만 추출하여 반환한다")
    void process_homepageHtmlEntity_returnsUrl() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage(
                "&lt;a href=&quot;https://blog.naver.com/kktm2021&quot; target=&quot;_blank&quot;&gt;블로그&lt;/a&gt;");
        PlaceProcessingResult result = processor.process(dto);
        assertThat(result.place().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    @DisplayName("homepage가 href 속성이 없는 a 태그 형태이면 내부 텍스트에서 태그를 지우고 반환한다")
    void process_homepageHtmlNoHref_returnsText() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage("<a>https://blog.naver.com/kktm2021</a>");
        PlaceProcessingResult result = processor.process(dto);
        assertThat(result.place().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    @DisplayName("homepage에 2개 이상의 URL이 발견되면 IllegalArgumentException을 발생시킨다")
    void process_homepageMultipleUrls_throwsIllegalArgumentException() {
        TourApiItemDto dto =
                createDtoWithHomepage("<a href=\"https://url1.com\"></a> <a href=\"https://url2.com\"></a>");
        assertThatThrownBy(() -> processor.process(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multiple URLs found in homepage");
    }

    @Test
    @DisplayName("homepage에서 추출한 결과가 유효한 URL 형식이 아니면 IllegalArgumentException을 발생시킨다")
    void process_homepageInvalidUrlFormat_throwsIllegalArgumentException() {
        TourApiItemDto dto = createDtoWithHomepage("<a>가경터미널시장 블로그</a>");
        assertThatThrownBy(() -> processor.process(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid URL format extracted");
    }

    @Test
    @DisplayName("homepage가 들어왔으나 URL을 추출해낼 수 없으면 IllegalArgumentException을 발생시킨다")
    void process_homepageNoExtractedUrl_throwsIllegalArgumentException() {
        TourApiItemDto dto = createDtoWithHomepage("<a></a>");
        assertThatThrownBy(() -> processor.process(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No valid URL could be extracted");
    }
}
