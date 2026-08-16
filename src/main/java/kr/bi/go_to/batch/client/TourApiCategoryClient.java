package kr.bi.go_to.batch.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import kr.bi.go_to.batch.dto.TourApiCategoryItem;
import kr.bi.go_to.batch.dto.TourApiCategoryPage;
import kr.bi.go_to.batch.exception.TourApiInfrastructureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Component
public class TourApiCategoryClient {

    private static final String API_NAME = "lclsSystmCode2";

    private final RestClient restClient;

    @Value("${tour-api.service-key:}")
    private String serviceKey;

    @Value("${tour-api.base-url:}")
    private String baseUrl;

    @Value("${tour-api.mobile-os:ETC}")
    private String mobileOs;

    @Value("${tour-api.mobile-app:AppTest}")
    private String mobileApp;

    public TourApiCategoryClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public TourApiCategoryPage fetchPage(int pageNo, int numOfRows) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/" + API_NAME)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("_type", "json")
                .queryParam("lclsSystmListYn", "Y")
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows)
                .build(true)
                .toUri();

        try {
            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null) {
                throw new TourApiInfrastructureException("Tour API taxonomy response body is empty");
            }

            String resultCode = root.at("/response/header/resultCode").asString();
            if (!"0000".equals(resultCode)) {
                String resultMessage = root.at("/response/header/resultMsg").asString();
                throw new TourApiInfrastructureException("Tour API taxonomy request failed: resultCode=%s, resultMsg=%s"
                        .formatted(resultCode, resultMessage));
            }

            JsonNode body = root.at("/response/body");
            return new TourApiCategoryPage(
                    requiredInteger(body, "pageNo"),
                    requiredInteger(body, "numOfRows"),
                    requiredInteger(body, "totalCount"),
                    readItems(body.at("/items/item")));
        } catch (TourApiInfrastructureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TourApiInfrastructureException("Failed to fetch Tour API taxonomy page " + pageNo, exception);
        }
    }

    private int requiredInteger(JsonNode body, String field) {
        JsonNode value = body.at("/" + field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new TourApiInfrastructureException("Tour API taxonomy pagination field must be an integer: " + field);
        }
        return value.asInt();
    }

    private List<TourApiCategoryItem> readItems(JsonNode itemNode) {
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }

        List<TourApiCategoryItem> items = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) {
                items.add(toItem(node));
            }
        } else if (itemNode.isObject()) {
            items.add(toItem(itemNode));
        } else {
            throw new TourApiInfrastructureException("Tour API taxonomy items has an invalid shape");
        }
        return items;
    }

    private TourApiCategoryItem toItem(JsonNode node) {
        return new TourApiCategoryItem(
                text(node, "lclsSystm1Cd"),
                text(node, "lclsSystm1Nm"),
                text(node, "lclsSystm2Cd"),
                text(node, "lclsSystm2Nm"),
                text(node, "lclsSystm3Cd"),
                text(node, "lclsSystm3Nm"));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.at("/" + field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
