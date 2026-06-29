package kr.bi.go_to.batch.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
public class TourApiBaseItemProcessor implements ItemProcessor<TourApiItemDto, PlaceProcessingResult> {

    private static final Pattern HREF_PATTERN =
            Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_FIND_PATTERN =
            Pattern.compile("https?://[^\\s<>\"']+|www\\.[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final EtlFailureLogger etlFailureLogger;

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
        String homepage = sanitizeHomepage(dto.homepage());
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

    private String sanitizeHomepage(String homepage) {
        if (homepage == null) {
            return null;
        }

        if (!StringUtils.hasText(homepage)) {
            return "";
        }

        // HTML 엔티티 디코딩 (예: &lt; -> <)
        String unescaped = HtmlUtils.htmlUnescape(homepage);

        // 1. 2개 이상의 서로 다른 URL이 포함되어 있는지 검사
        int urlCount = countDistinctUrls(unescaped);
        if (urlCount > 1) {
            throw new HomepageParsingException(HomepageParsingErrorType.MULTIPLE_URLS, homepage);
        }

        // 2. URL 추출 시도
        String extracted;
        Matcher matcher = HREF_PATTERN.matcher(unescaped);
        if (matcher.find()) {
            extracted = matcher.group(1).trim();
        } else {
            // href 속성이 없는 경우, 태그를 제거하고 남은 텍스트를 대상 URL로 삼음
            extracted = unescaped.replaceAll("<[^>]*>", "").trim();
        }

        // 3. 홈페이지 정보가 들어왔으나 유효한 URL로 정제되지 않은 경우 예외 처리
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
