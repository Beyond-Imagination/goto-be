package kr.bi.go_to.batch.processor;

import java.util.ArrayList;
import java.util.List;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.enums.PlaceSource;
import kr.bi.go_to.model.place.Place;
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
public class TourApiItemProcessor implements ItemProcessor<TourApiItemDto, Place> {

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    public Place process(TourApiItemDto dto) throws Exception {
        if (!StringUtils.hasText(dto.getContentid())) {
            log.warn("Skipping item with empty contentid: {}", dto.getTitle());
            return null;
        }

        if (!StringUtils.hasText(dto.getTitle())) {
            log.warn("Skipping item with empty title: contentid={}", dto.getContentid());
            return null;
        }

        Point location = null;
        if (StringUtils.hasText(dto.getMapx()) && StringUtils.hasText(dto.getMapy())) {
            try {
                double lon = Double.parseDouble(dto.getMapx());
                double lat = Double.parseDouble(dto.getMapy());

                // x = longitude, y = latitude
                // Korea bounding box roughly: lon 124~132, lat 33~43
                if (lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90) {
                    location = geometryFactory.createPoint(new Coordinate(lon, lat));
                } else {
                    log.warn(
                            "Invalid coordinates range for contentid {}: mapx={}, mapy={}",
                            dto.getContentid(),
                            lon,
                            lat);
                }
            } catch (NumberFormatException e) {
                log.warn(
                        "Invalid coordinates format for contentid {}: mapx={}, mapy={}",
                        dto.getContentid(),
                        dto.getMapx(),
                        dto.getMapy());
            }
        }

        String address = constructAddress(dto.getAddr1(), dto.getAddr2());
        String thumbnailUrl = StringUtils.hasText(dto.getFirstimage())
                ? dto.getFirstimage()
                : (StringUtils.hasText(dto.getFirstimage2()) ? dto.getFirstimage2() : null);

        return Place.builder()
                .externalId(dto.getContentid())
                .source(PlaceSource.TOUR_API.name())
                .name(dto.getTitle())
                .sanitizedAddress(address)
                .locationPoint(location)
                .thumbnailUrl(thumbnailUrl)
                .tel(dto.getTel())
                .contentTypeId(dto.getContenttypeid())
                .category(dto.getCat3())
                .build();
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
}
