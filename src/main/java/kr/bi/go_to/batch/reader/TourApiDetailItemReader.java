package kr.bi.go_to.batch.reader;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.model.place.Place;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
public class TourApiDetailItemReader implements ItemReader<TourApiItemDto> {

    private final JdbcTemplate jdbcTemplate;
    private final TourApiClient tourApiClient;
    private final ThreadPoolTaskExecutor detailTaskExecutor;
    private final Queue<TourApiItemDto> itemBuffer = new LinkedList<>();
    private boolean isInitialized = false;

    @Value("${tour-api.detail-quota:1000}")
    private int detailQuota;

    public TourApiDetailItemReader(
            JdbcTemplate jdbcTemplate,
            TourApiClient tourApiClient,
            @Qualifier("tourApiDetailTaskExecutor") ThreadPoolTaskExecutor detailTaskExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.tourApiClient = tourApiClient;
        this.detailTaskExecutor = detailTaskExecutor;
    }

    @Override
    public TourApiItemDto read() throws Exception {
        if (!isInitialized) {
            initialize();
            isInitialized = true;
        }

        return itemBuffer.poll();
    }

    private void initialize() {
        log.info("상세 정보 보충이 필요한 장소를 최대 {}개까지 조회합니다...", detailQuota);

        String sql =
                "SELECT external_id, source, category, name, sanitized_address, location_point, thumbnail_url, content_type_id, tel "
                        + "FROM places WHERE overview IS NULL AND source = 'TOUR_API' LIMIT ?";

        List<Place> placesToEnrich = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    return Place.builder()
                            .externalId(rs.getString("external_id"))
                            .source(rs.getString("source"))
                            .category(rs.getString("category"))
                            .name(rs.getString("name"))
                            .sanitizedAddress(rs.getString("sanitized_address"))
                            .thumbnailUrl(rs.getString("thumbnail_url"))
                            .contentTypeId(rs.getString("content_type_id"))
                            .tel(rs.getString("tel"))
                            .build();
                },
                detailQuota);

        log.info(
                "상세 정보 보충이 필요한 장소 {}개를 발견했습니다. 동시성 {} 수준으로 비동기 수집을 시작합니다.",
                placesToEnrich.size(),
                detailTaskExecutor.getCorePoolSize());

        List<CompletableFuture<TourApiItemDto>> futures = placesToEnrich.stream()
                .map(place -> CompletableFuture.supplyAsync(() -> fetchDetailsForPlace(place), detailTaskExecutor))
                .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<TourApiItemDto> future : futures) {
            try {
                TourApiItemDto result = future.get();
                if (result != null) {
                    itemBuffer.add(result);
                }
            } catch (Exception e) {
                log.error("Error retrieving async result", e);
            }
        }
        log.info("Enrichment complete. Buffer size: {}", itemBuffer.size());
    }

    private TourApiItemDto fetchDetailsForPlace(Place place) {
        String contentId = place.getExternalId();
        String contentTypeId = place.getContentTypeId();

        JsonNode common2 = tourApiClient.fetchDetail("detailCommon2", contentId, null);
        String overview = tourApiClient.extractField(common2, "overview");
        String homepage = tourApiClient.extractField(common2, "homepage");

        JsonNode withTour2 = tourApiClient.fetchDetail("detailWithTour2", contentId, null);
        String bfDetails = withTour2 != null ? withTour2.toString() : null;

        JsonNode intro2 = tourApiClient.fetchDetail("detailIntro2", contentId, contentTypeId);
        String introDetails = intro2 != null ? intro2.toString() : null;

        return new TourApiItemDto(
                place.getExternalId(),
                place.getContentTypeId(),
                place.getName(),
                null, // addr1
                null, // addr2
                null, // mapx
                null, // mapy
                place.getCategory(), // cat1 etc mapped to category
                null,
                null,
                place.getThumbnailUrl(),
                null,
                null,
                null,
                place.getTel(),
                null,
                null,
                overview,
                homepage,
                bfDetails,
                introDetails,
                "1");
    }
}
