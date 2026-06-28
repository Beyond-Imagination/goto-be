package kr.bi.go_to.batch.reader;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.LinkedList;
import java.util.Queue;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.dto.TourApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@StepScope
public class TourApiItemReader implements ItemReader<TourApiItemDto> {

    private final RestClient restClient;

    @Value("${tour-api.service-key}")
    private String serviceKey;

    @Value("${tour-api.base-url}")
    private String baseUrl;

    @Value("${tour-api.mobile-os:ETC}")
    private String mobileOs;

    @Value("${tour-api.mobile-app:AppTest}")
    private String mobileApp;

    private int currentPage = 1;
    private final int numOfRows = 1000;
    private boolean isFullyRead = false;
    private int totalCount = -1;
    private final Queue<TourApiItemDto> itemBuffer = new LinkedList<>();

    public TourApiItemReader(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public TourApiItemDto read() throws Exception {
        if (isFullyRead && itemBuffer.isEmpty()) {
            return null;
        }

        if (itemBuffer.isEmpty()) {
            fetchNextPage();
        }

        if (itemBuffer.isEmpty()) {
            isFullyRead = true;
            return null;
        }

        TourApiItemDto item = itemBuffer.poll();
        return enrichWithDetails(item);
    }

    private TourApiItemDto enrichWithDetails(TourApiItemDto item) {
        String contentId = item.contentid();
        String contentTypeId = item.contenttypeid();

        JsonNode common2 = fetchDetail("detailCommon2", contentId, null);
        String overview = extractField(common2, "overview");
        String homepage = extractField(common2, "homepage");

        JsonNode withTour2 = fetchDetail("detailWithTour2", contentId, null);
        String bfDetails = withTour2 != null ? withTour2.toString() : null;

        JsonNode intro2 = fetchDetail("detailIntro2", contentId, contentTypeId);
        String introDetails = intro2 != null ? intro2.toString() : null;

        return item.withDetails(overview, homepage, bfDetails, introDetails);
    }

    private JsonNode fetchDetail(String apiName, String contentId, String contentTypeId) {
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

    private String extractField(JsonNode node, String fieldName) {
        if (node != null) {
            JsonNode fieldNode = node.at("/" + fieldName);
            if (!fieldNode.isMissingNode() && !fieldNode.isNull()) {
                String val = fieldNode.asText();
                return val.isEmpty() ? null : val;
            }
        }
        return null;
    }

    private void fetchNextPage() {
        if (totalCount != -1 && (currentPage - 1) * numOfRows >= totalCount) {
            isFullyRead = true;
            return;
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/areaBasedList2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("pageNo", currentPage)
                .queryParam("numOfRows", numOfRows)
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("_type", "json")
                .build(true) // 이미 인코딩된 키를 사용할 예정이므로, 별도로 추가 인코딩을 수행하지 않을 예정
                .toUri();

        log.info("Fetching Tour API page: {}, url: {}", currentPage, uri);

        try {
            TourApiResponseDto responseDto =
                    restClient.get().uri(uri).retrieve().body(TourApiResponseDto.class);

            if (responseDto != null
                    && responseDto.getResponse() != null
                    && responseDto.getResponse().getBody() != null) {
                TourApiResponseDto.Body body = responseDto.getResponse().getBody();

                if (totalCount == -1) {
                    totalCount = body.getTotalCount();
                }

                if (body.getItems() != null && body.getItems().getItem() != null) {
                    itemBuffer.addAll(body.getItems().getItem());
                    log.info(
                            "Fetched {} items from page {}. Total count: {}",
                            body.getItems().getItem().size(),
                            currentPage,
                            body.getTotalCount());
                    currentPage++;
                } else {
                    log.info("No items found on page {}", currentPage);
                    currentPage++;
                    if ((currentPage - 1) * numOfRows >= totalCount) {
                        isFullyRead = true;
                    }
                }
            } else {
                log.error("Invalid response structure from Tour API");
                isFullyRead = true;
            }
        } catch (Exception e) {
            log.error("Error fetching Tour API data on page {}", currentPage, e);
            throw e;
        }
    }
}
