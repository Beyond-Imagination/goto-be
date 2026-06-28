package kr.bi.go_to.batch.processor;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.HomepageParsingErrorType;
import kr.bi.go_to.batch.exception.HomepageParsingException;
import kr.bi.go_to.batch.listener.EtlFailureLogger;
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
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiIncrementalItemProcessor implements ItemProcessor<TourApiItemDto, PlaceProcessingResult> {

    private static final Pattern HREF_PATTERN =
            Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_FIND_PATTERN =
            Pattern.compile("https?://[^\\s<>\"']+|www\\.[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final EtlFailureLogger etlFailureLogger;
    private final TourApiClient tourApiClient;

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

        // Check showflag
        if ("0".equals(dto.showflag())) {
            isDeleted = true;
        } else {
            // Eager Fetch for newly added/updated items
            try {
                JsonNode common2 = tourApiClient.fetchDetail("detailCommon2", dto.contentid(), null);
                overview = tourApiClient.extractField(common2, "overview");
                String rawHomepage = tourApiClient.extractField(common2, "homepage");
                homepage = sanitizeHomepage(rawHomepage);

                JsonNode withTour2 = tourApiClient.fetchDetail("detailWithTour2", dto.contentid(), null);
                bfDetails = withTour2 != null ? withTour2.toString() : null;

                JsonNode intro2 = tourApiClient.fetchDetail("detailIntro2", dto.contentid(), dto.contenttypeid());
                introDetails = intro2 != null ? intro2.toString() : null;
            } catch (HomepageParsingException hpe) {
                // If homepage parsing fails, we bubble it up so skip listener logs it
                throw hpe;
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
                .build();

        return new PlaceProcessingResult(place, bfDetails, introDetails);
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

    private String sanitizeHomepage(String homepage) {
        if (!StringUtils.hasText(homepage)) {
            return null;
        }

        String unescaped = HtmlUtils.htmlUnescape(homepage);

        int urlCount = countDistinctUrls(unescaped);
        if (urlCount > 1) {
            throw new HomepageParsingException(HomepageParsingErrorType.MULTIPLE_URLS, homepage);
        }

        String extracted;
        Matcher matcher = HREF_PATTERN.matcher(unescaped);
        if (matcher.find()) {
            extracted = matcher.group(1).trim();
        } else {
            extracted = unescaped.replaceAll("<[^>]*>", "").trim();
        }

        if (!StringUtils.hasText(extracted)) {
            throw new HomepageParsingException(HomepageParsingErrorType.NO_EXTRACTED_URL, homepage);
        }

        if (!isValidUrl(extracted)) {
            throw new HomepageParsingException(HomepageParsingErrorType.INVALID_URL_FORMAT, extracted, homepage);
        }

        return extracted;
    }

    private int countDistinctUrls(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        Set<String> urls = new HashSet<>();
        Matcher matcher = URL_FIND_PATTERN.matcher(text);
        while (matcher.find()) {
            urls.add(matcher.group().toLowerCase().trim());
        }
        return urls.size();
    }

    private boolean isValidUrl(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.");
    }
}
