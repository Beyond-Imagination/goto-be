package kr.bi.go_to.batch.client;

import java.net.URI;
import kr.bi.go_to.batch.exception.TourApiInfrastructureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

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

        if (requiresContentTypeId(apiName) && contentTypeId != null) {
            builder.queryParam("contentTypeId", contentTypeId);
        }

        URI uri = builder.build(true).toUri();

        try {
            JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null) {
                throw new TourApiInfrastructureException(
                        "Tour API detail response body is empty: apiName=%s, contentId=%s"
                                .formatted(apiName, contentId));
            }
            validateResult(response, apiName, contentId);

            JsonNode item = response.at("/response/body/items/item");
            if (item.isArray() && !item.isEmpty()) {
                return item.get(0);
            }
            if (item.isObject()) {
                return item;
            }
        } catch (TourApiInfrastructureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TourApiInfrastructureException(
                    "Failed to fetch Tour API detail: apiName=%s, contentId=%s".formatted(apiName, contentId),
                    exception);
        }
        return null;
    }

    private boolean requiresContentTypeId(String apiName) {
        return "detailIntro2".equals(apiName);
    }

    private void validateResult(JsonNode response, String apiName, String contentId) {
        JsonNode resultCodeNode = response.at("/response/header/resultCode");
        String resultCode = resultCodeNode.isMissingNode() || resultCodeNode.isNull() ? "" : resultCodeNode.asString();
        if ("0000".equals(resultCode)) {
            return;
        }

        String resultMsg = response.at("/response/header/resultMsg").asString();
        throw new TourApiInfrastructureException(
                "Tour API detail request failed: apiName=%s, contentId=%s, resultCode=%s, resultMsg=%s"
                        .formatted(apiName, contentId, resultCode, resultMsg));
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
