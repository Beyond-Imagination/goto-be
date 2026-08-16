package kr.bi.go_to.batch.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import kr.bi.go_to.batch.exception.TourApiInfrastructureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TourApiClientTest {

    @Test
    @DisplayName("Tour API가 배열 item JSON을 주면 fetchDetail은 첫 item을 JsonNode로 반환한다")
    void fetchDetailReadsJsonResponseIntoJackson3JsonNode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            byte[] response =
                    """
                    {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[{"contentid":"130376","overview":"ok"}]}}}}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            TourApiClient client = new TourApiClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            JsonNode detail = client.fetchDetail("detailCommon2", "130376", null);

            assertThat(detail).isNotNull();
            assertThat(detail.at("/overview").asString()).isEqualTo("ok");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Tour API가 단일 객체 item JSON을 주면 fetchDetail은 해당 item을 JsonNode로 반환한다")
    void fetchDetailAcceptsSingleObjectItemResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            byte[] response =
                    """
                    {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":{"contentid":"130376","overview":"ok"}}}}}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            TourApiClient client = new TourApiClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            JsonNode detail = client.fetchDetail("detailCommon2", "130376", null);

            assertThat(detail).isNotNull();
            assertThat(detail.at("/overview").asString()).isEqualTo("ok");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("contentTypeId가 있으면 detailIntro2 요청 query parameter에 contentTypeId를 포함한다")
    void fetchDetailIncludesContentTypeIdAndRequiredQueryParametersWhenContentTypeIdExists() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailIntro2", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response =
                    """
                    {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[{"contentid":"130376","contenttypeid":"12"}]}}}}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            TourApiClient client = new TourApiClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "test-service-key");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            JsonNode detail = client.fetchDetail("detailIntro2", "130376", "12");

            assertThat(detail).isNotNull();
            assertThat(rawQuery.get())
                    .contains("serviceKey=test-service-key")
                    .contains("MobileOS=ETC")
                    .contains("MobileApp=Goto")
                    .contains("_type=json")
                    .contains("contentId=130376")
                    .contains("contentTypeId=12");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("contentTypeId가 있어도 detailCommon2 요청 query parameter에는 contentTypeId를 넣지 않는다")
    void fetchDetailOmitsContentTypeIdForApisThatDoNotRequireIt() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response =
                    """
                    {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[{"contentid":"130376","overview":"ok"}]}}}}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            TourApiClient client = new TourApiClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "test-service-key");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            JsonNode detail = client.fetchDetail("detailCommon2", "130376", "12");

            assertThat(detail).isNotNull();
            assertThat(rawQuery.get())
                    .contains("serviceKey=test-service-key")
                    .contains("MobileOS=ETC")
                    .contains("MobileApp=Goto")
                    .contains("_type=json")
                    .contains("contentId=130376")
                    .doesNotContain("contentTypeId=");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Tour API resultCode가 성공이 아니면 비스킵 인프라 예외를 발생시킨다")
    void fetchDetailFailsImmediatelyWhenTourApiResultIsNotOk() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            byte[] response =
                    """
                    {"response":{"header":{"resultCode":"0003","resultMsg":"인증키가 유효하지 않습니다."},"body":{"items":{"item":[{"contentid":"130376","overview":"ok"}]}}}}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            TourApiClient client = new TourApiClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            assertThatThrownBy(() -> client.fetchDetail("detailCommon2", "130376", null))
                    .isInstanceOf(TourApiInfrastructureException.class)
                    .hasMessageContaining("apiName=detailCommon2")
                    .hasMessageContaining("contentId=130376")
                    .hasMessageContaining("resultCode=0003")
                    .hasMessageContaining("resultMsg=인증키가 유효하지 않습니다.");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Tour API resultCode가 누락되거나 공백이면 비스킵 인프라 예외를 발생시킨다")
    void fetchDetailFailsImmediatelyWhenResultCodeIsMissingOrBlank() throws Exception {
        AtomicReference<String> responseBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            TourApiClient client = new TourApiClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            responseBody.set(
                    """
                    {"response":{"header":{},"body":{"items":{"item":[]}}}}
                    """);
            assertThatThrownBy(() -> client.fetchDetail("detailCommon2", "missing", null))
                    .isInstanceOf(TourApiInfrastructureException.class)
                    .hasMessageContaining("resultCode=");

            responseBody.set(
                    """
                    {"response":{"header":{"resultCode":" "},"body":{"items":{"item":[]}}}}
                    """);
            assertThatThrownBy(() -> client.fetchDetail("detailCommon2", "blank", null))
                    .isInstanceOf(TourApiInfrastructureException.class)
                    .hasMessageContaining("resultCode= ");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Tour API가 빈 HTTP 본문을 반환하면 비스킵 인프라 예외를 발생시킨다")
    void fetchDetailFailsImmediatelyWhenResponseBodyIsEmpty() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            TourApiClient client = new TourApiClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            assertThatThrownBy(() -> client.fetchDetail("detailCommon2", "130376", null))
                    .isInstanceOf(TourApiInfrastructureException.class)
                    .hasMessageContaining("response body is empty");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("JsonNode 필드가 없거나 blank/null이면 extractFieldOrEmpty는 빈 문자열 또는 null을 반환한다")
    void extractFieldOrEmptyReturnsEmptyStringForMissingOrBlankFieldsWhenNodeExists() throws Exception {
        TourApiClient client = new TourApiClient(RestClient.builder());
        JsonNode node = JsonMapper.builder()
                .build()
                .readTree(
                        """
                        {"present":"value","blank":"","nullish":null}
                        """);

        assertThat(client.extractFieldOrEmpty(null, "present")).isNull();
        assertThat(client.extractFieldOrEmpty(node, "missing")).isEmpty();
        assertThat(client.extractFieldOrEmpty(node, "blank")).isEmpty();
        assertThat(client.extractFieldOrEmpty(node, "nullish")).isEmpty();
        assertThat(client.extractFieldOrEmpty(node, "present")).isEqualTo("value");
    }
}
