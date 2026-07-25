package kr.bi.go_to.batch.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

class TourApiHomepageNormalizerTest {

    @Test
    @DisplayName("빈 홈페이지 원문은 홈페이지 없음 의미로 빈 문자열을 반환한다")
    void normalize_blankHomepage_returnsEmptyString() {
        assertThat(TourApiHomepageNormalizer.normalize("   ")).isEmpty();
    }

    @Test
    @DisplayName("라벨과 URL이 함께 있으면 URL만 추출한다")
    void normalize_labelAndUrl_returnsUrlOnly() {
        assertThat(TourApiHomepageNormalizer.normalize("문경 문화관광 https://www.gbmg.go.kr/tour"))
                .isEqualTo("https://www.gbmg.go.kr/tour");
    }

    @Test
    @DisplayName("단일 Instagram handle은 Instagram URL로 변환한다")
    void normalize_instagramHandle_returnsInstagramUrl() {
        assertThat(TourApiHomepageNormalizer.normalize("@bbang.dabang"))
                .isEqualTo("https://www.instagram.com/bbang.dabang");
    }

    @Test
    @DisplayName("scheme이 없는 bare domain은 https URL로 변환한다")
    void normalize_bareDomain_returnsHttpsUrl() {
        assertThat(TourApiHomepageNormalizer.normalize("airbnb.co.kr/h/yangstay"))
                .isEqualTo("https://airbnb.co.kr/h/yangstay");
        assertThat(TourApiHomepageNormalizer.normalize("ulvine.com")).isEqualTo("https://ulvine.com");
    }

    @Test
    @DisplayName("a 태그의 href가 상세 URL이고 text가 축약 URL이면 href를 저장한다")
    void normalize_anchorWithDetailHref_returnsHref() {
        String raw =
                """
                <a href="https://www.seosan.go.kr/tour/guideView.do?key=5976&guidanceNo=52" target="_blank">
                  https://www.seosan.go.kr
                </a>
                """;

        assertThat(TourApiHomepageNormalizer.normalize(raw))
                .isEqualTo("https://www.seosan.go.kr/tour/guideView.do?key=5976&guidanceNo=52");
    }

    @Test
    @DisplayName("href에 줄바꿈이 섞여 있어도 공백을 제거해 저장한다")
    void normalize_hrefWithLineBreak_removesWhitespace() {
        String raw =
                """
                <a href="https://www.ygtour.kr/Home/H20000/H20400/placeDetail?place_no=51
                " target="_blank">http://www.ygtour.kr</a>
                """;

        assertThat(TourApiHomepageNormalizer.normalize(raw))
                .isEqualTo("https://www.ygtour.kr/Home/H20000/H20400/placeDetail?place_no=51");
    }

    @Test
    @DisplayName("일반 URL과 Instagram URL이 함께 있으면 일반 URL을 대표로 선택한다")
    void normalize_primaryAndInstagram_returnsPrimaryUrl() {
        String raw = "공식 홈페이지 http://gnhcc.co.kr 공식 인스타그램 https://www.instagram.com/gn_hcc/";

        assertThat(TourApiHomepageNormalizer.normalize(raw)).isEqualTo("http://gnhcc.co.kr");
    }

    @Test
    @DisplayName("판단할 수 없는 일반 URL이 여러 개면 null을 반환한다")
    void normalize_multiplePrimaryUrls_returnsNull() {
        String raw = "강화군 문화관광 https://www.ganghwa.go.kr/tour/ 국가유산청 https://www.khs.go.kr/main.html";

        assertThat(TourApiHomepageNormalizer.normalize(raw)).isNull();
    }

    @Test
    @DisplayName("보조 채널만 여러 개면 null을 반환한다")
    void normalize_multipleAuxiliaryUrls_returnsNull() {
        String raw =
                """
                <a href="https://m.smartstore.naver.com/wanjudaldalguri">스토어</a>
                <a href="https://instagram.com/daldalguri_official">인스타그램</a>
                <a href="https://m.blog.naver.com/daldalguri_">블로그</a>
                """;

        assertThat(TourApiHomepageNormalizer.normalize(raw)).isNull();
    }

    @Test
    @DisplayName("이메일 주소는 홈페이지 URL 후보로 보지 않는다")
    void normalize_emailAddress_returnsNull() {
        assertThat(TourApiHomepageNormalizer.normalize("문의 test@example.com")).isNull();
    }

    @Test
    @DisplayName("실제 홈페이지 파싱 실패 유형 fixture를 정책대로 정규화한다")
    void normalize_realFailureTypeFixture_returnsExpectedResult() throws Exception {
        for (HomepageNormalizationFixture fixture : loadHomepageNormalizationFixture()) {
            String normalized = TourApiHomepageNormalizer.normalize(fixture.rawHomepage());

            if (fixture.expectedHomepage() == null) {
                assertThat(normalized)
                        .as("type=%s raw=%s", fixture.type(), fixture.rawHomepage())
                        .isNull();
            } else {
                assertThat(normalized)
                        .as("type=%s raw=%s", fixture.type(), fixture.rawHomepage())
                        .isEqualTo(fixture.expectedHomepage());
            }
        }
    }

    private List<HomepageNormalizationFixture> loadHomepageNormalizationFixture() throws Exception {
        Path path = fixturePath("fixtures/tour-api-homepage-normalization-cases.tsv");
        return Files.readAllLines(path).stream()
                .skip(1)
                .filter(StringUtils::hasText)
                .map(line -> line.split("\t", 3))
                .map(columns -> new HomepageNormalizationFixture(
                        columns[0], columns[1].replace("\\n", "\n"), parseExpectedHomepage(columns[2])))
                .toList();
    }

    private Path fixturePath(String resourcePath) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource(resourcePath).toURI());
    }

    private String parseExpectedHomepage(String value) {
        return "__NULL__".equals(value) ? null : value.replace("\\n", "\n");
    }

    private record HomepageNormalizationFixture(String type, String rawHomepage, String expectedHomepage) {}
}
