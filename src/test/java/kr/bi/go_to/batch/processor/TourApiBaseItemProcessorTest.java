package kr.bi.go_to.batch.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.listener.EtlFailureLogger;
import kr.bi.go_to.enums.PlaceSource;
import kr.bi.go_to.model.place.Place;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TourApiBaseItemProcessorTest {

    private TourApiBaseItemProcessor processor;
    private EtlFailureLogger etlFailureLogger;

    @BeforeEach
    void setUp() {
        etlFailureLogger = mock(EtlFailureLogger.class);
        processor = new TourApiBaseItemProcessor(etlFailureLogger);
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
                null, // introDetails
                "1" // showflag
                );
    }

    @Test
    @DisplayName("정상 DTO를 process하면 Place 엔티티로 변환한다")
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
    @DisplayName("contentid가 없으면 process하면 null을 반환한다")
    void process_emptyContentId_returnsNull() throws Exception {
        // given
        TourApiItemDto dto = createDto("", "Test Place", null, null, null, null, null, null, null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("title이 없으면 process하면 null을 반환한다")
    void process_emptyTitle_returnsNull() throws Exception {
        // given
        TourApiItemDto dto = createDto("12345", "", null, null, null, null, null, null, null, null, null);

        // when
        PlaceProcessingResult result = processor.process(dto);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("좌표가 유효 범위를 벗어나면 process하면 locationPoint는 null이고 ETL 실패 로그를 기록한다")
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
        verify(etlFailureLogger)
                .logFailure(
                        "12345",
                        "[COORDINATE_OUT_OF_BOUNDS] Invalid coordinates range: mapx=200.0, mapy=100.0, --> contentId: 12345");
    }

    @Test
    @DisplayName("좌표 포맷이 잘못됐으면 process하면 locationPoint는 null이고 ETL 실패 로그를 기록한다")
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
        verify(etlFailureLogger)
                .logFailure(
                        "12345",
                        "[COORDINATE_FORMAT_ERROR] Invalid coordinates format: mapx=invalid, mapy=invalid, --> contentId: 12345");
    }

    @Test
    @DisplayName("firstimage가 없고 firstimage2만 있으면 process하면 firstimage2를 썸네일로 사용한다")
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
    @DisplayName("firstimage와 firstimage2가 모두 없으면 process하면 썸네일은 null이다")
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
    @DisplayName("주소가 하나뿐이면 process하면 쉼표 없이 해당 주소만 저장한다")
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
                null, // introDetails
                "1" // showflag
                );
    }

    @Test
    @DisplayName("homepage가 일반 URL이면 process하면 그대로 반환한다")
    void process_homepagePlainUrl_returnsPlainUrl() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage("https://blog.naver.com/kktm2021");
        PlaceProcessingResult result = processor.process(dto);
        assertThat(result.place().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    @DisplayName("overview와 homepage가 빈 문자열이면 process하면 외부 API 값 없음 상태로 빈 문자열을 보존한다")
    void process_emptyOverviewAndHomepage_preservesEmptyStrings() throws Exception {
        TourApiItemDto dto = new TourApiItemDto(
                "12345",
                "12",
                "Test Place",
                "Seoul",
                "Gangnam",
                "127.0",
                "37.0",
                null,
                null,
                "A0101",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                "",
                null,
                null,
                "1",
                true,
                false,
                false);

        PlaceProcessingResult result = processor.process(dto);

        assertThat(result.place().getOverview()).isEmpty();
        assertThat(result.place().getHomepage()).isEmpty();
    }

    @Test
    @DisplayName("homepage가 HTML a 태그 형식이면 process하면 URL만 추출해 반환한다")
    void process_homepageHtmlTag_returnsUrl() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage(
                "<a href=\"https://blog.naver.com/kktm2021\" target=\"_blank\" title=\"새창\">https://blog.naver.com/kktm2021</a>");
        PlaceProcessingResult result = processor.process(dto);
        assertThat(result.place().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    @DisplayName("homepage가 HTML 엔티티 a 태그 형식이면 process하면 URL만 추출해 반환한다")
    void process_homepageHtmlEntity_returnsUrl() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage(
                "&lt;a href=&quot;https://blog.naver.com/kktm2021&quot; target=&quot;_blank&quot;&gt;블로그&lt;/a&gt;");
        PlaceProcessingResult result = processor.process(dto);
        assertThat(result.place().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    @DisplayName("homepage가 href 없는 a 태그 형식이면 process하면 내부 텍스트에서 URL을 추출한다")
    void process_homepageHtmlNoHref_returnsText() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage("<a>https://blog.naver.com/kktm2021</a>");
        PlaceProcessingResult result = processor.process(dto);
        assertThat(result.place().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    @DisplayName("homepage에 판단할 수 없는 일반 URL이 2개 이상이면 process하면 homepage는 null이다")
    void process_homepageMultiplePrimaryUrls_returnsNullHomepage() throws Exception {
        TourApiItemDto dto =
                createDtoWithHomepage("강화군 문화관광 https://www.ganghwa.go.kr/tour/ 국가유산청 https://www.khs.go.kr/main.html");

        PlaceProcessingResult result = processor.process(dto);

        assertThat(result.place().getHomepage()).isNull();
    }

    @Test
    @DisplayName("homepage가 라벨과 URL로 구성되어 있으면 process하면 URL만 추출한다")
    void process_homepageLabelAndUrl_returnsUrl() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage("<a>가경터미널시장 블로그</a>");

        PlaceProcessingResult result =
                processor.process(dto.withDetails(null, "문경 문화관광 https://www.gbmg.go.kr/tour", null, null));

        assertThat(result.place().getHomepage()).isEqualTo("https://www.gbmg.go.kr/tour");
    }

    @Test
    @DisplayName("homepage에서 URL을 추출할 수 없으면 process하면 homepage는 null이다")
    void process_homepageNoExtractedUrl_returnsNullHomepage() throws Exception {
        TourApiItemDto dto = createDtoWithHomepage("<a></a>");

        PlaceProcessingResult result = processor.process(dto);

        assertThat(result.place().getHomepage()).isNull();
    }
}
