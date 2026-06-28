package kr.bi.go_to.batch.reader;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Queue;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.dto.TourApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@StepScope
public class TourApiIncrementalItemReader implements ItemReader<TourApiItemDto> {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestClient restClient;
    private final JdbcTemplate jdbcTemplate;

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

    private String lastSyncTime;
    private boolean isInitialized = false;

    public TourApiIncrementalItemReader(RestClient.Builder restClientBuilder, JdbcTemplate jdbcTemplate) {
        this.restClient = restClientBuilder.build();
        this.jdbcTemplate = jdbcTemplate;
    }

    private void initialize() {
        String sql =
                "SELECT target_date FROM batch_sync_log WHERE status = 'SUCCESS' AND job_name = 'tourApiIncrementalSyncJob' ORDER BY created_at DESC LIMIT 1";
        try {
            this.lastSyncTime = jdbcTemplate.queryForObject(sql, String.class);
            log.info("Found last sync time: {}", lastSyncTime);
        } catch (EmptyResultDataAccessException e) {
            // 초기 적재 직후이거나 로그가 없을 경우 전일 데이터 기준으로 동기화
            this.lastSyncTime = LocalDate.now(KST).minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            log.warn("No successful sync log found. Defaulting lastSyncTime to {}", lastSyncTime);
        }
        isInitialized = true;
    }

    @Override
    public TourApiItemDto read() throws Exception {
        if (!isInitialized) {
            initialize();
        }

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

        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/areaBasedSyncList1")
                .queryParam("serviceKey", serviceKey)
                .queryParam("pageNo", currentPage)
                .queryParam("numOfRows", numOfRows)
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("modifiedtime", lastSyncTime)
                .queryParam("_type", "json")
                .build(true)
                .toUri();

        log.info("Fetching Tour API Incremental page: {}, url: {}", currentPage, uri);

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
                            "Fetched {} incremental items from page {}. Total count: {}",
                            body.getItems().getItem().size(),
                            currentPage,
                            body.getTotalCount());
                    currentPage++;
                } else {
                    log.info("No items found on incremental page {}", currentPage);
                    currentPage++;
                    if ((currentPage - 1) * numOfRows >= totalCount) {
                        isFullyRead = true;
                    }
                }
            } else {
                log.error("Invalid response structure from Tour API Incremental Sync");
                isFullyRead = true;
            }
        } catch (Exception e) {
            log.error("Error fetching Tour API incremental data on page {}", currentPage, e);
            throw e;
        }
    }
}
