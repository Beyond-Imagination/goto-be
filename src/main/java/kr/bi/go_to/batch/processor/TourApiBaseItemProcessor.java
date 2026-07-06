package kr.bi.go_to.batch.processor;

import java.util.ArrayList;
import java.util.List;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiBaseItemProcessor implements ItemProcessor<TourApiItemDto, PlaceProcessingResult> {

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final EtlFailureLogger etlFailureLogger;
    private final TourApiHomepageNormalizer homepageNormalizer;

    private static final String FAILURE_LOG_TEMPLATE = "[%s] %s, --> contentId: %s";

    private void handleFailure(String prefix, String cause, String contentId) {
        String msg = String.format(FAILURE_LOG_TEMPLATE, prefix, cause, contentId);
        log.warn(msg);
        etlFailureLogger.logFailure(contentId, msg);
    }

    @Override
    public PlaceProcessingResult process(TourApiItemDto dto) throws Exception {
        if (!StringUtils.hasText(dto.contentid())) {
            log.warn("Skipping item with empty contentid: {}", dto.title());
            return null;
        }

        if (!StringUtils.hasText(dto.title())) {
            log.warn("Skipping item with empty title: contentid={}", dto.contentid());
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
        String thumbnailUrl = StringUtils.hasText(dto.firstimage())
                ? dto.firstimage()
                : (StringUtils.hasText(dto.firstimage2()) ? dto.firstimage2() : null);

        String overview = normalizeKnownEmptyText(dto.overview());
        String homepage = homepageNormalizer.normalize(dto.homepage());
        String tel = StringUtils.hasText(dto.tel()) ? dto.tel() : null;

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
                .detailCommonSynced(dto.detailCommonSynced())
                .detailWithTourSynced(dto.detailWithTourSynced())
                .detailIntroSynced(dto.detailIntroSynced())
                .build();

        return new PlaceProcessingResult(
                place,
                dto.bfDetails(),
                dto.introDetails(),
                dto.detailCommonSynced(),
                dto.detailWithTourSynced(),
                dto.detailIntroSynced());
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

    private String normalizeKnownEmptyText(String value) {
        if (value == null) {
            return null;
        }
        return StringUtils.hasText(value) ? value : "";
    }

    private boolean isLocationWithinValidBounds(double lon, double lat) {
        // 대한민국 영토 및 영해(독도, 이어도 등 포함)를 커버하는 넉넉한 바운딩 박스
        return lon >= 124.0 && lon <= 132.0 && lat >= 32.0 && lat <= 43.5;
    }
}
