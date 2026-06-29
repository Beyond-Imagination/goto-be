package kr.bi.go_to.batch.client;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

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
                if (hasNonOkResult(response, apiName, contentId)) {
                    return null;
                }

                JsonNode item = response.at("/response/body/items/item");
                if (item.isArray() && !item.isEmpty()) {
                    return item.get(0);
                }
                if (item.isObject()) {
                    return item;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch detail API: {} for contentId: {}", apiName, contentId, e);
        }
        return null;
    }

    private boolean hasNonOkResult(JsonNode response, String apiName, String contentId) {
        JsonNode resultCodeNode = response.at("/response/header/resultCode");
        if (resultCodeNode.isMissingNode() || resultCodeNode.isNull()) {
            return false;
        }

        String resultCode = resultCodeNode.asString();
        if (resultCode.isBlank() || "0000".equals(resultCode)) {
            return false;
        }

        String resultMsg = response.at("/response/header/resultMsg").asString();
        log.warn(
                "Tour API 상세 조회 실패 응답입니다. apiName={}, contentId={}, resultCode={}, resultMsg={}",
                apiName,
                contentId,
                resultCode,
                resultMsg);
        return true;
    }

    public String extractFieldOrEmpty(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }

        JsonNode fieldNode = node.at("/" + fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return "";
        }

        String val = fieldNode.asString();
        return val == null ? "" : val;
    }
}
