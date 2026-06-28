package kr.bi.go_to.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class TourApiRestClientConfigTest {

    @Test
    void restClientDoesNotSendHttp2UpgradeHeaders() throws Exception {
        AtomicReference<List<String>> upgradeHeaders = new AtomicReference<>(List.of());
        AtomicReference<List<String>> http2SettingsHeaders = new AtomicReference<>(List.of());

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/probe", exchange -> {
            upgradeHeaders.set(exchange.getRequestHeaders().getOrDefault("Upgrade", List.of()));
            http2SettingsHeaders.set(exchange.getRequestHeaders().getOrDefault("HTTP2-Settings", List.of()));

            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            RestClient.Builder builder = RestClient.builder();
            new TourApiRestClientConfig().tourApiHttp11RestClientCustomizer().customize(builder);

            String body = builder.build()
                    .get()
                    .uri("http://localhost:%d/probe"
                            .formatted(server.getAddress().getPort()))
                    .retrieve()
                    .body(String.class);

            assertThat(body).isEqualTo("{\"ok\":true}");
            assertThat(upgradeHeaders.get()).isEmpty();
            assertThat(http2SettingsHeaders.get()).isEmpty();
        } finally {
            server.stop(0);
        }
    }
}
