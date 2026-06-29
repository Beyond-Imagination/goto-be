package kr.bi.go_to.batch.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class TourApiClientTest {

    @Test
    void fetchDetailReadsJsonResponseIntoJackson3JsonNode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            byte[] response =
                    """
                    {"response":{"body":{"items":{"item":[{"contentid":"130376","overview":"ok"}]}}}}
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
    @DisplayName("Tour API detail 응답의 item이 단일 객체여도 정상 상세 데이터로 처리한다")
    void fetchDetailAcceptsSingleObjectItemResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            byte[] response =
                    """
                    {"response":{"body":{"items":{"item":{"contentid":"130376","overview":"ok"}}}}}
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
    @DisplayName("contentTypeId가 있으면 Tour API 상세 요청 query parameter에 포함한다")
    void fetchDetailIncludesContentTypeIdAndRequiredQueryParametersWhenContentTypeIdExists() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailIntro2", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response =
                    """
                    {"response":{"body":{"items":{"item":[{"contentid":"130376","contenttypeid":"12"}]}}}}
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
    @DisplayName("detailCommon2 요청에는 contentTypeId를 포함하지 않는다")
    void fetchDetailOmitsContentTypeIdForApisThatDoNotRequireIt() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response =
                    """
                    {"response":{"body":{"items":{"item":[{"contentid":"130376","overview":"ok"}]}}}}
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
    @DisplayName("Tour API resultCode가 성공이 아니면 한국어 경고 로그를 남기고 상세 데이터를 누락 처리한다")
    void fetchDetailLogsKoreanMessageAndReturnsNullWhenTourApiResultIsNotOk(CapturedOutput output) throws Exception {
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

            JsonNode detail = client.fetchDetail("detailCommon2", "130376", null);

            assertThat(detail).isNull();
            assertThat(output)
                    .contains("Tour API 상세 조회 실패 응답입니다")
                    .contains("apiName=detailCommon2")
                    .contains("contentId=130376")
                    .contains("resultCode=0003")
                    .contains("resultMsg=인증키가 유효하지 않습니다.");
        } finally {
            server.stop(0);
        }
    }

    @Test
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
