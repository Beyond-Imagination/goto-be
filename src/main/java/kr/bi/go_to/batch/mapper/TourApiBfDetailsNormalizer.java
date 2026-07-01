package kr.bi.go_to.batch.mapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Tour API의 detailWithTour2 원본 flat JSON을 place_bf_info.bf_details JSONB 스키마로 정규화한다.
 *
 * <p>저장 스키마는 {@link kr.bi.go_to.model.place.PlaceBfDetails}와 맞춘다. 최상위 키는 항상
 * mobility, visual, hearing, infant_family, intro, sources를 가진다. 각 편의시설 카테고리는 이
 * 클래스에 정의된 알려진 Tour API 필드를 모두 포함한다.
 *
 * <p>Tour API가 비어 있거나 제공하지 않은 값은 "없음(false)"으로 추론하지 않는다. 외부 응답만으로
 * 이용 가능 여부를 판단할 수 없다는 뜻으로 BfItem의 is_available, count, details를 모두 null로 둔다.
 * 반대로 원문 설명이 있는 경우에만 is_available=true로 저장하고, count는 괄호 안 숫자를 추출할 수 있을
 * 때만 채운다.
 *
 * <pre>
 * {
 *   "mobility": {
 *     "parking": {"is_available": true, "count": 9, "details": "장애인 전용 주차구역 있음(9대)"},
 *     "elevator": {"is_available": null, "count": null, "details": null}
 *   },
 *   "visual": { ... },
 *   "hearing": { ... },
 *   "infant_family": { ... },
 *   "intro": { "contentid": "1067369", "usetime": "09:00~18:00" },
 *   "sources": {
 *     "tour_api": {
 *       "externalId": "1067369",
 *       "syncedAt": "2026-07-01T00:00:00Z",
 *       "detailWithTour": { ... },
 *       "detailIntro": { ... }
 *     }
 *   }
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class TourApiBfDetailsNormalizer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Pattern COUNT_IN_PARENTHESES = Pattern.compile("\\((\\d+)\\s*[^)]*\\)");

    private static final List<String> MOBILITY_FIELDS = List.of(
            "parking",
            "route",
            "publictransport",
            "ticketoffice",
            "promotion",
            "wheelchair",
            "exit",
            "elevator",
            "restroom",
            "auditorium",
            "room",
            "handicapetc");

    private static final List<String> VISUAL_FIELDS = List.of(
            "braileblock",
            "helpdog",
            "guidehuman",
            "audioguide",
            "bigprint",
            "brailepromotion",
            "guidesystem",
            "blindhandicapetc");

    private static final List<String> HEARING_FIELDS =
            List.of("signguide", "videoguide", "hearingroom", "hearinghandicapetc");

    private static final List<String> INFANT_FAMILY_FIELDS =
            List.of("stroller", "lactationroom", "babysparechair", "infantsfamilyetc");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * detailWithTour2 원본과 detailIntro2 원본을 PlaceBfDetails 스키마 JSON 문자열로 변환한다.
     *
     * @param externalId Tour API contentid에서 온 장소 외부 식별자
     * @param bfDetailsJson detailWithTour2 item JSON. parking, route 같은 flat field를 가진다.
     * @param introDetailsJson detailIntro2 item JSON. intro 아래 원문 그대로 보존한다.
     * @return place_bf_info.bf_details에 저장할 정규화 JSON 문자열
     */
    public String normalize(String externalId, String bfDetailsJson, String introDetailsJson) {
        if (!StringUtils.hasText(externalId)) {
            throw new IllegalArgumentException("externalId is required to normalize barrier-free details");
        }

        Map<String, Object> bfDetails = readJsonObject(bfDetailsJson);
        Map<String, Object> normalized = new LinkedHashMap<>();

        putCategory(normalized, "mobility", bfDetails, MOBILITY_FIELDS);
        putCategory(normalized, "visual", bfDetails, VISUAL_FIELDS);
        putCategory(normalized, "hearing", bfDetails, HEARING_FIELDS);
        putCategory(normalized, "infant_family", bfDetails, INFANT_FAMILY_FIELDS);

        Map<String, Object> introDetails = readJsonObject(introDetailsJson);
        normalized.put("intro", introDetails);
        normalized.put("sources", createTourApiSources(externalId.trim(), bfDetails, introDetails));

        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to write normalized barrier-free details JSON", e);
        }
    }

    private Map<String, Object> createTourApiSources(
            String externalId, Map<String, Object> bfDetails, Map<String, Object> introDetails) {
        Map<String, Object> tourApi = new LinkedHashMap<>();
        tourApi.put("externalId", externalId);
        tourApi.put("externalSubId", null);
        tourApi.put("evalInfo", null);
        tourApi.put("syncedAt", Instant.now(clock));
        tourApi.put("detailWithTour", bfDetails);
        tourApi.put("detailIntro", introDetails);

        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("tour_api", tourApi);
        return sources;
    }

    private Map<String, Object> readJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to read barrier-free details JSON", e);
        }
    }

    private void putCategory(
            Map<String, Object> normalized, String categoryName, Map<String, Object> source, List<String> fields) {
        Map<String, Object> category = new LinkedHashMap<>();
        for (String field : fields) {
            String details = asNonBlankString(source.get(field));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("is_available", details == null ? null : true);
            item.put("count", details == null ? null : extractCount(details));
            item.put("details", details);
            category.put(field, item);
        }

        normalized.put(categoryName, category);
    }

    private String asNonBlankString(Object value) {
        if (!(value instanceof String string) || !StringUtils.hasText(string)) {
            return null;
        }
        return string.trim();
    }

    private Integer extractCount(String details) {
        Matcher matcher = COUNT_IN_PARENTHESES.matcher(details);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
