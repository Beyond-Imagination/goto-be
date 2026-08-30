package kr.bi.go_to.obstaclereport.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import kr.bi.go_to.service.obstaclereport.geocoding.NaverReverseGeocodingClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

class NaverReverseGeocodingClientTest {

    @Test
    @DisplayName("region.area2/area3 이름을 조합해 \"구 동\" 라벨을 반환한다")
    void reverseGeocodeReturnsCombinedAdministrativeAreaName() throws Exception {
        HttpServer server = startServer(
                """
                {"status":{"code":0,"name":"ok"},"results":[{"name":"admcode","region":{
                  "area1":{"name":"서울특별시"},"area2":{"name":"종로구"},"area3":{"name":"청운동"}
                }}]}
                """,
                200);

        try {
            NaverReverseGeocodingClient client = newClient(server, "id", "secret");

            Optional<String> label = client.reverseGeocode(37.5665, 126.978);

            assertThat(label).contains("종로구 청운동");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("client-id/client-secret이 비어있으면 호출 없이 empty를 반환한다")
    void reverseGeocodeSkipsCallWhenCredentialsAreBlank() {
        NaverReverseGeocodingClient client = new NaverReverseGeocodingClient();
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:1");
        ReflectionTestUtils.setField(client, "clientId", "");
        ReflectionTestUtils.setField(client, "clientSecret", "");

        Optional<String> label = client.reverseGeocode(37.5665, 126.978);

        assertThat(label).isEmpty();
    }

    @Test
    @DisplayName("results가 비어있으면 empty를 반환한다")
    void reverseGeocodeReturnsEmptyWhenNoResults() throws Exception {
        HttpServer server = startServer(
                """
                {"status":{"code":0,"name":"ok"},"results":[]}
                """, 200);

        try {
            NaverReverseGeocodingClient client = newClient(server, "id", "secret");

            Optional<String> label = client.reverseGeocode(37.5665, 126.978);

            assertThat(label).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("API 호출이 실패해도 예외를 던지지 않고 empty를 반환한다")
    void reverseGeocodeSwallowsFailureAndReturnsEmpty() throws Exception {
        HttpServer server = startServer("internal error", 500);

        try {
            NaverReverseGeocodingClient client = newClient(server, "id", "secret");

            Optional<String> label = client.reverseGeocode(37.5665, 126.978);

            assertThat(label).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    private NaverReverseGeocodingClient newClient(HttpServer server, String clientId, String clientSecret) {
        NaverReverseGeocodingClient client = new NaverReverseGeocodingClient();
        ReflectionTestUtils.setField(
                client,
                "baseUrl",
                "http://localhost:%d".formatted(server.getAddress().getPort()));
        ReflectionTestUtils.setField(client, "clientId", clientId);
        ReflectionTestUtils.setField(client, "clientSecret", clientSecret);
        return client;
    }

    private HttpServer startServer(String responseBody, int statusCode) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }
}
