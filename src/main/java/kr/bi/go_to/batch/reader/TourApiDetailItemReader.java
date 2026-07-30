package kr.bi.go_to.batch.reader;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryException;
import kr.bi.go_to.batch.exception.TourApiInfrastructureException;
import kr.bi.go_to.batch.validation.TourApiPlaceCategoryValidator;
import kr.bi.go_to.model.batch.CategoryResolutionStatus;
import kr.bi.go_to.model.batch.DetailSyncStatus;
import kr.bi.go_to.model.place.Place;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
@StepScope
public class TourApiDetailItemReader implements ItemReader<TourApiItemDto> {

    private final JdbcTemplate jdbcTemplate;
    private final TourApiClient tourApiClient;
    private final TourApiPlaceCategoryValidator categoryValidator;
    private final ThreadPoolTaskExecutor detailTaskExecutor;
    private final Queue<TourApiItemDto> itemBuffer = new LinkedList<>();
    private boolean isInitialized = false;

    @Value("${tour-api.detail-quota:250}")
    private int detailQuota;

    public TourApiDetailItemReader(
            JdbcTemplate jdbcTemplate,
            TourApiClient tourApiClient,
            TourApiPlaceCategoryValidator categoryValidator,
            @Qualifier("tourApiDetailTaskExecutor") ThreadPoolTaskExecutor detailTaskExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.tourApiClient = tourApiClient;
        this.categoryValidator = categoryValidator;
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
                "SELECT external_id, source, category_code, name, sanitized_address, location_point, thumbnail_url, content_type_id, tel "
                        + ", category_resolution_status, detail_common_status, detail_with_tour_status, detail_intro_status "
                        + "FROM places WHERE source = 'TOUR_API' AND is_deleted = false "
                        + "AND (category_resolution_status = ? OR detail_common_status = ? "
                        + "OR detail_with_tour_status = ? OR detail_intro_status = ?) "
                        + "ORDER BY updated_at ASC, id ASC "
                        + "LIMIT ?";

        List<Place> placesToEnrich = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    return Place.builder()
                            .externalId(rs.getString("external_id"))
                            .source(rs.getString("source"))
                            .categoryCode(rs.getString("category_code"))
                            .name(rs.getString("name"))
                            .sanitizedAddress(rs.getString("sanitized_address"))
                            .thumbnailUrl(rs.getString("thumbnail_url"))
                            .contentTypeId(rs.getString("content_type_id"))
                            .tel(rs.getString("tel"))
                            .categoryResolutionStatus(
                                    CategoryResolutionStatus.valueOf(rs.getString("category_resolution_status")))
                            .detailCommonStatus(DetailSyncStatus.valueOf(rs.getString("detail_common_status")))
                            .detailWithTourStatus(DetailSyncStatus.valueOf(rs.getString("detail_with_tour_status")))
                            .detailIntroStatus(DetailSyncStatus.valueOf(rs.getString("detail_intro_status")))
                            .build();
                },
                CategoryResolutionStatus.PENDING.name(),
                DetailSyncStatus.PENDING.name(),
                DetailSyncStatus.PENDING.name(),
                DetailSyncStatus.PENDING.name(),
                detailQuota);

        log.info(
                "상세 정보 보충이 필요한 장소 {}개를 발견했습니다. 동시성 {} 수준으로 비동기 수집을 시작합니다.",
                placesToEnrich.size(),
                detailTaskExecutor.getCorePoolSize());

        List<CompletableFuture<TourApiItemDto>> futures = placesToEnrich.stream()
                .map(place -> CompletableFuture.supplyAsync(() -> fetchDetailsForPlace(place), detailTaskExecutor))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<TourApiItemDto> future : futures) {
            TourApiItemDto result = future.join();
            if (result != null) {
                itemBuffer.add(result);
            }
        }
        log.info("Enrichment complete. Buffer size: {}", itemBuffer.size());
    }

    private TourApiItemDto fetchDetailsForPlace(Place place) {
        String contentId = place.getExternalId();
        String contentTypeId = place.getContentTypeId();

        DetailFetch commonResponse = fetchCommon(place);
        DetailFetch common = preserveTerminalStatus(place.getDetailCommonStatus(), commonResponse);
        CategoryResolution category = resolveCategory(place, commonResponse);
        DetailFetch withTour =
                resolveDetail(category.status(), place.getDetailWithTourStatus(), "detailWithTour2", contentId, null);
        DetailFetch intro = resolveDetail(
                category.status(), place.getDetailIntroStatus(), "detailIntro2", contentId, contentTypeId);

        return detailDto(place, category, common, withTour, intro);
    }

    private DetailFetch fetchCommon(Place place) {
        if (place.getDetailCommonStatus() == DetailSyncStatus.PENDING
                || place.getCategoryResolutionStatus() == CategoryResolutionStatus.PENDING) {
            return fetch("detailCommon2", place.getExternalId(), null);
        }
        return retained(place.getDetailCommonStatus());
    }

    private CategoryResolution resolveCategory(Place place, DetailFetch commonResponse) {
        if (place.getCategoryResolutionStatus() != CategoryResolutionStatus.PENDING) {
            return new CategoryResolution(place.getCategoryCode(), place.getCategoryResolutionStatus());
        }
        if (commonResponse.status() == DetailSyncStatus.PENDING) {
            return new CategoryResolution(place.getCategoryCode(), CategoryResolutionStatus.PENDING);
        }
        if (commonResponse.node() == null) {
            return new CategoryResolution(place.getCategoryCode(), CategoryResolutionStatus.NOT_FOUND);
        }

        String categoryCode = tourApiClient.extractFieldOrEmpty(commonResponse.node(), "lclsSystm3");
        if (categoryCode == null || categoryCode.isBlank()) {
            return new CategoryResolution(null, CategoryResolutionStatus.NOT_FOUND);
        }

        try {
            String activeLeaf = categoryValidator.requireActiveLeaf(place.getExternalId(), categoryCode);
            return new CategoryResolution(activeLeaf, CategoryResolutionStatus.RESOLVED);
        } catch (InvalidTourApiCategoryException exception) {
            log.warn(
                    "복구된 Tour API category가 활성 leaf가 아니어서 terminal 처리합니다. contentId={}, categoryCode={}",
                    place.getExternalId(),
                    categoryCode);
            return new CategoryResolution(null, CategoryResolutionStatus.NOT_FOUND);
        }
    }

    private DetailFetch resolveDetail(
            CategoryResolutionStatus categoryStatus,
            DetailSyncStatus currentStatus,
            String apiName,
            String contentId,
            String contentTypeId) {
        return switch (categoryStatus) {
            case PENDING -> retained(currentStatus);
            case NOT_FOUND -> skipPending(currentStatus);
            case RESOLVED -> currentStatus == DetailSyncStatus.PENDING
                    ? fetch(apiName, contentId, contentTypeId)
                    : retained(currentStatus);
        };
    }

    private DetailFetch fetch(String apiName, String contentId, String contentTypeId) {
        try {
            JsonNode node = tourApiClient.fetchDetail(apiName, contentId, contentTypeId);
            return new DetailFetch(node, node == null ? DetailSyncStatus.NOT_FOUND : DetailSyncStatus.SUCCESS);
        } catch (TourApiInfrastructureException exception) {
            log.warn("Tour API 상세 정보 조회에 실패하여 대기 상태로 유지합니다. apiName={}, contentId={}", apiName, contentId);
            return new DetailFetch(null, DetailSyncStatus.PENDING);
        }
    }

    private DetailFetch preserveTerminalStatus(DetailSyncStatus currentStatus, DetailFetch response) {
        if (response.node() != null || currentStatus == DetailSyncStatus.PENDING) {
            return response;
        }
        return new DetailFetch(null, currentStatus);
    }

    private DetailFetch skipPending(DetailSyncStatus currentStatus) {
        DetailSyncStatus nextStatus =
                currentStatus == DetailSyncStatus.PENDING ? DetailSyncStatus.SKIPPED : currentStatus;
        return retained(nextStatus);
    }

    private DetailFetch retained(DetailSyncStatus status) {
        return new DetailFetch(null, status);
    }

    private TourApiItemDto detailDto(
            Place place, CategoryResolution category, DetailFetch common, DetailFetch withTour, DetailFetch intro) {
        String overview = common.node() != null ? tourApiClient.extractFieldOrEmpty(common.node(), "overview") : null;
        String homepage = common.node() != null ? tourApiClient.extractFieldOrEmpty(common.node(), "homepage") : null;
        String bfDetails = withTour.node() != null ? withTour.node().toString() : null;
        String introDetails = intro.node() != null ? intro.node().toString() : null;

        return new TourApiItemDto(
                place.getExternalId(),
                place.getContentTypeId(),
                place.getName(),
                null, // 기본 주소 유지
                null, // 상세 주소 유지
                null, // 경도 유지
                null, // 위도 유지
                null, // 대분류 유지
                null, // 중분류 유지
                category.code(),
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
                "1",
                common.status() == DetailSyncStatus.SUCCESS,
                withTour.status() == DetailSyncStatus.SUCCESS,
                intro.status() == DetailSyncStatus.SUCCESS,
                category.status(),
                common.status(),
                withTour.status(),
                intro.status());
    }

    private record CategoryResolution(String code, CategoryResolutionStatus status) {}

    private record DetailFetch(JsonNode node, DetailSyncStatus status) {}
}
