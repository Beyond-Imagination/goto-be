package kr.bi.go_to.service.obstaclereport.geocoding;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import kr.bi.go_to.properties.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * 네이버 클라우드 플랫폼 Reverse Geocoding API 연동. 프론트가 이미 Naver Map SDK를 쓰고 있어
 * 같은 벤더로 통일했다 (docs/adr/0006 참고).
 *
 * <p>외부 API 실패(네트워크 오류, 인증 실패, quota 초과, 매칭 결과 없음 등)는 예외를 던지지 않고
 * {@code Optional.empty()}로 흡수한다 — 클러스터 API 전체가 이 라벨링 하나 때문에 실패해선 안 되며,
 * {@link kr.bi.go_to.config.RedisCacheErrorHandler}가 캐시 장애를 다루는 것과 같은 원칙이다.
 */
@Slf4j
@Component
public class NaverReverseGeocodingClient {

    /** 행정동 기준 조회. 시/군/구(area2) + 읍/면/동(area3)을 라벨로 조합한다. */
    private static final String ORDERS = "admcode";

    /**
     * 중간 줌 클러스터 라벨링은 읽기 트랜잭션 밖에서 호출되긴 하지만(ObstacleReportService 참고),
     * 그래도 이 호출이 무한정 걸리면 응답 자체가 지연된다. NCP 장애/혼잡 시에도 API가 합리적인
     * 시간 안에 (라벨 없이) 응답할 수 있도록 짧게 끊는다.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;

    @Value("${naver-reverse-geocoding.base-url:https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc}")
    private String baseUrl;

    @Value("${naver-reverse-geocoding.client-id:}")
    private String clientId;

    @Value("${naver-reverse-geocoding.client-secret:}")
    private String clientSecret;

    /**
     * 애플리케이션 전역에 주입되는 {@link RestClient.Builder} 빈은 재사용 목적상 스코프가
     * prototype이 아닌 경우(예: 테스트의 Mock 설정) 여러 소비자가 같은 인스턴스를 공유할 수
     * 있는데, {@code requestFactory(...)}는 그 빌더 인스턴스를 직접 변경(mutate)한다. 공유
     * 빌더를 여기서 바꾸면 다른 컴포넌트가 만드는 RestClient까지 이 타임아웃/HTTP1.1 설정을
     * 의도치 않게 물려받을 수 있어(테스트에서 Tour API mock RestClient가 실제로 이 문제를
     * 겪었다), 주입받지 않고 완전히 독립된 빌더로 만든다.
     */
    public NaverReverseGeocodingClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * 좌표를 행정동 이름("OO구 OO동")으로 변환한다. 키 미설정, 호출 실패, 매칭 결과 없음은 모두 empty.
     *
     * <p>인접한 클러스터가 같은 행정동으로 묶이는 경우가 흔해 좌표를 ~100m 격자로 반올림한 키로
     * 캐싱한다(Redis, 기본 TTL). 실패/empty 결과는 caching하지 않는다({@code unless}) — 일시적인
     * 장애 때문에 "라벨 없음"이 TTL 동안 굳어버리는 걸 막기 위함이다.
     */
    @Cacheable(
            value = CacheProperties.NEARBY_PLACE_LABEL,
            key = "T(Math).round(#lat * 1000) + ':' + T(Math).round(#lng * 1000)",
            // Optional<T> 반환값은 캐시 계층에서 언랩되어 empty()는 #result가 null로 평가된다.
            unless = "#result == null")
    public Optional<String> reverseGeocode(double lat, double lng) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return Optional.empty();
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("coords", lng + "," + lat)
                .queryParam("orders", ORDERS)
                .queryParam("output", "json")
                .build(true)
                .toUri();

        try {
            JsonNode response = restClient
                    .get()
                    .uri(uri)
                    .header("x-ncp-apigw-api-key-id", clientId)
                    .header("x-ncp-apigw-api-key", clientSecret)
                    .retrieve()
                    .body(JsonNode.class);

            return extractAdministrativeAreaName(response);
        } catch (Exception exception) {
            log.warn("네이버 리버스 지오코딩 실패, 라벨 없이 진행합니다. lat={}, lng={}", lat, lng, exception);
            return Optional.empty();
        }
    }

    private Optional<String> extractAdministrativeAreaName(JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }

        JsonNode results = response.at("/results");
        if (!results.isArray() || results.isEmpty()) {
            return Optional.empty();
        }

        JsonNode region = results.get(0).at("/region");
        String gu = textOrEmpty(region.at("/area2/name"));
        String dong = textOrEmpty(region.at("/area3/name"));
        String label = (gu + " " + dong).trim();

        return label.isEmpty() ? Optional.empty() : Optional.of(label);
    }

    private String textOrEmpty(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "" : node.asString();
    }
}
