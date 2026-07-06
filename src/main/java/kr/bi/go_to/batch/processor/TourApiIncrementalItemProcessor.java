package kr.bi.go_to.batch.processor;

import java.util.ArrayList;
import java.util.List;
import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.listener.EtlFailureLogger;
import kr.bi.go_to.batch.mapper.TourApiHomepageNormalizer;
import kr.bi.go_to.enums.PlaceSource;
import kr.bi.go_to.model.place.Place;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiIncrementalItemProcessor implements ItemProcessor<TourApiItemDto, PlaceProcessingResult> {

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final EtlFailureLogger etlFailureLogger;
    private final TourApiClient tourApiClient;
    private final TourApiHomepageNormalizer homepageNormalizer;

    private static final String FAILURE_LOG_TEMPLATE = "[%s] %s, --> contentId: %s";

    private void handleFailure(String prefix, String cause, String contentId) {
        String msg = String.format(FAILURE_LOG_TEMPLATE, prefix, cause, contentId);
        log.warn(msg);
        etlFailureLogger.logFailure(contentId, msg);
    }

    @Override
    public PlaceProcessingResult process(TourApiItemDto dto) throws Exception {
        // Validation: Mandatory fields
        if (!StringUtils.hasText(dto.contentid())) {
            log.warn("Skipping item with empty contentid: {}", dto.title());
            return null;
        }

        if (!StringUtils.hasText(dto.title())) {
            handleFailure("MISSING_REQUIRED_FIELD", "Title is empty", dto.contentid());
            return null;
        }

        // Validation: Length constraints
        if (dto.title().length() > 255) {
            handleFailure("EXCEED_MAX_LENGTH", "Title length > 255", dto.contentid());
            return null;
        }

        Point location = null;
        if (StringUtils.hasText(dto.mapx()) && StringUtils.hasText(dto.mapy())) {
            try {
                double lon = Double.parseDouble(dto.mapx());
                double lat = Double.parseDouble(dto.mapy());

                if (isLocationWithinValidBounds(lon, lat)) {
                    location = geometryFactory.createPoint(new Coordinate(lon, lat));
                } else {
                    String cause = String.format("Invalid coordinates range: mapx=%s, mapy=%s", dto.mapx(), dto.mapy());
                    handleFailure("COORDINATE_OUT_OF_BOUNDS", cause, dto.contentid());
                }
            } catch (NumberFormatException e) {
                String cause = String.format("Invalid coordinates format: mapx=%s, mapy=%s", dto.mapx(), dto.mapy());
                handleFailure("COORDINATE_FORMAT_ERROR", cause, dto.contentid());
            }
        }

        String address = constructAddress(dto.addr1(), dto.addr2());
        if (address != null && address.length() > 500) {
            handleFailure("EXCEED_MAX_LENGTH", "Address length > 500", dto.contentid());
            return null;
        }

        String thumbnailUrl = StringUtils.hasText(dto.firstimage())
                ? dto.firstimage()
                : (StringUtils.hasText(dto.firstimage2()) ? dto.firstimage2() : null);

        String tel = StringUtils.hasText(dto.tel()) ? dto.tel() : null;

        boolean isDeleted = false;
        String overview = null;
        String homepage = null;
        String bfDetails = null;
        String introDetails = null;
        boolean detailCommonSynced = false;
        boolean detailWithTourSynced = false;
        boolean detailIntroSynced = false;

        // Check showflag
        if ("0".equals(dto.showflag())) {
            isDeleted = true;
        } else {
            // Eager Fetch for newly added/updated items
            try {
                JsonNode common2 = tourApiClient.fetchDetail("detailCommon2", dto.contentid(), null);
                detailCommonSynced = common2 != null;
                if (detailCommonSynced) {
                    overview = tourApiClient.extractFieldOrEmpty(common2, "overview");
                    String rawHomepage = tourApiClient.extractFieldOrEmpty(common2, "homepage");
                    homepage = homepageNormalizer.normalize(rawHomepage);
                }

                JsonNode withTour2 = tourApiClient.fetchDetail("detailWithTour2", dto.contentid(), null);
                detailWithTourSynced = withTour2 != null;
                bfDetails = withTour2 != null ? withTour2.toString() : null;

                JsonNode intro2 = tourApiClient.fetchDetail("detailIntro2", dto.contentid(), dto.contenttypeid());
                detailIntroSynced = intro2 != null;
                introDetails = intro2 != null ? intro2.toString() : null;
            } catch (Exception e) {
                log.warn("Eager fetch failed for contentId: {}, fallback to lazy fetch later.", dto.contentid(), e);
                // Leave overview = null to trigger lazy fetch in Step 2
            }
        }

        Place place = Place.builder()
                .externalId(dto.contentid())
                .source(PlaceSource.TOUR_API.name())
                .name(dto.title())
                .sanitizedAddress(address)
                .locationPoint(location)
                .thumbnailUrl(thumbnailUrl)
                .tel(tel)
                .overview(overview)
                .homepage(homepage)
                .contentTypeId(dto.contenttypeid())
                .category(dto.cat3())
                .isDeleted(isDeleted)
                .detailCommonSynced(detailCommonSynced)
                .detailWithTourSynced(detailWithTourSynced)
                .detailIntroSynced(detailIntroSynced)
                .build();

        return new PlaceProcessingResult(
                place, bfDetails, introDetails, detailCommonSynced, detailWithTourSynced, detailIntroSynced);
    }

    private String constructAddress(String addr1, String addr2) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(addr1)) {
            parts.add(addr1.trim());
        }
        if (StringUtils.hasText(addr2)) {
            parts.add(addr2.trim());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private boolean isLocationWithinValidBounds(double lon, double lat) {
        return lon >= 124.0 && lon <= 132.0 && lat >= 32.0 && lat <= 43.5;
    }
}
