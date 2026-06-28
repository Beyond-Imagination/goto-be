package kr.bi.go_to.batch.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class TourApiClient {

    private final RestClient restClient;

    @Value("${tour-api.service-key:}")
    private String serviceKey;

    @Value("${tour-api.base-url:}")
    private String baseUrl;

    @Value("${tour-api.mobile-os:ETC}")
    private String mobileOs;

    @Value("${tour-api.mobile-app:AppTest}")
    private String mobileApp;

    public TourApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public JsonNode fetchDetail(String apiName, String contentId, String contentTypeId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/" + apiName)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("_type", "json")
                .queryParam("contentId", contentId);

        if (contentTypeId != null) {
            builder.queryParam("contentTypeId", contentTypeId);
        }

        URI uri = builder.build(true).toUri();

        try {
            JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (response != null) {
                JsonNode items = response.at("/response/body/items/item");
                if (items.isArray() && items.size() > 0) {
                    return items.get(0);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch detail API: {} for contentId: {}", apiName, contentId, e);
        }
        return null;
    }

    public String extractField(JsonNode node, String fieldName) {
        if (node != null) {
            JsonNode fieldNode = node.at("/" + fieldName);
            if (!fieldNode.isMissingNode() && !fieldNode.isNull()) {
                String val = fieldNode.asText();
                return val.isEmpty() ? null : val;
            }
        }
        return null;
    }
}
