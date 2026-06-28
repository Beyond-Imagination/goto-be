package kr.bi.go_to.batch.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

class TourApiClientTest {

    @Test
    void fetchDetailReadsJsonResponseIntoJackson3JsonNode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/detailCommon2", exchange -> {
            byte[] response = """
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
                    client, "baseUrl", "http://localhost:%d".formatted(server.getAddress().getPort()));
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
}
