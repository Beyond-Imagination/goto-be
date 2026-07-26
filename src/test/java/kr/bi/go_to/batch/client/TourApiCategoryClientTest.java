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

class TourApiCategoryClientTest {

    @Test
    @DisplayName("현재 분류체계 API를 요청하고 공식 대·중·소분류 필드를 페이지로 매핑한다")
    void requestsCurrentHierarchyEndpointAndMapsOfficialFields() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/lclsSystmCode2", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body =
                    """
                    {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
                      "pageNo":1,"numOfRows":100,"totalCount":1,
                      "items":{"item":[{"lclsSystm1Cd":"A","lclsSystm1Nm":"대",
                      "lclsSystm2Cd":"B","lclsSystm2Nm":"중",
                      "lclsSystm3Cd":"C","lclsSystm3Nm":"소"}]}}}}
                    """
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            TourApiCategoryClient client = new TourApiCategoryClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "key");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            var page = client.fetchPage(1, 100);

            assertThat(page.pageNo()).isEqualTo(1);
            assertThat(page.items()).singleElement().satisfies(item -> {
                assertThat(item.lclsSystm1Cd()).isEqualTo("A");
                assertThat(item.lclsSystm2Cd()).isEqualTo("B");
                assertThat(item.lclsSystm3Cd()).isEqualTo("C");
            });
            assertThat(rawQuery.get())
                    .contains("lclsSystmListYn=Y")
                    .contains("pageNo=1")
                    .contains("numOfRows=100");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("페이지네이션 필드가 누락되거나 숫자가 아니면 인프라 실패로 처리한다")
    void rejectsMissingOrNonNumericPaginationFieldsAsInfrastructureFailure() throws Exception {
        AtomicReference<String> responseBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/lclsSystmCode2", exchange -> {
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            TourApiCategoryClient client = new TourApiCategoryClient(RestClient.builder());
            ReflectionTestUtils.setField(
                    client,
                    "baseUrl",
                    "http://localhost:%d".formatted(server.getAddress().getPort()));
            ReflectionTestUtils.setField(client, "serviceKey", "key");
            ReflectionTestUtils.setField(client, "mobileOs", "ETC");
            ReflectionTestUtils.setField(client, "mobileApp", "Goto");

            responseBody.set(
                    """
                    {"response":{"header":{"resultCode":"0000"},"body":{
                      "numOfRows":100,"totalCount":1,"items":{"item":[]}}}}
                    """);
            assertThatThrownBy(() -> client.fetchPage(1, 100))
                    .isInstanceOf(TourApiInfrastructureException.class)
                    .hasMessageContaining("pageNo");

            responseBody.set(
                    """
                    {"response":{"header":{"resultCode":"0000"},"body":{
                      "pageNo":1,"numOfRows":"many","totalCount":1,"items":{"item":[]}}}}
                    """);
            assertThatThrownBy(() -> client.fetchPage(1, 100))
                    .isInstanceOf(TourApiInfrastructureException.class)
                    .hasMessageContaining("numOfRows");
        } finally {
            server.stop(0);
        }
    }
}
