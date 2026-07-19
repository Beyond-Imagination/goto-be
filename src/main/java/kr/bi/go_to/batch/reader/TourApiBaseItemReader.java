package kr.bi.go_to.batch.reader;

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
public class TourApiBaseItemReader implements ItemReader<TourApiItemDto> {

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

    public TourApiBaseItemReader(RestClient.Builder restClientBuilder) {
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

        return itemBuffer.poll();
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
