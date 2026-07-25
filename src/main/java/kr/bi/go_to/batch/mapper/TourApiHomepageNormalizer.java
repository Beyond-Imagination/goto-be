package kr.bi.go_to.batch.mapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Tour API로부터 수집된 원본 홈페이지 텍스트 데이터({@code rawHomepage})를 정규화하여
 * 대표 웹사이트 주소 또는 대표 SNS 채널 주소를 추출하는 stateless static utility입니다.
 *
 * <p>현재 버전에서는 다음과 같은 구체적인 동작을 수행합니다:
 * <ul>
 *   <li>HTML Entity 디코딩 및 공백 제거</li>
 *   <li>HTML {@code href} 속성, 일반 텍스트 {@code URL}, {@code Instagram Handle}(@username) 추출</li>
 *   <li>호스트({@code Host})별 그룹화 및 중복 제거</li>
 *   <li>보조 채널({@code Auxiliary Host}: Instagram, Facebook 등) 분류</li>
 *   <li>동일 도메인 내 최적의 {@code URL} 선택 ({@code href} 우선, {@code Path} 길이 우선, 탐색 순서 우선)</li>
 *   <li>다중 대표 도메인 또는 서로 다른 종류의 다중 보조 도메인이 혼재할 경우 대표성 모호함 방지를 위한 {@code null} 반환</li>
 * </ul>
 */
public final class TourApiHomepageNormalizer {

    private static final Pattern HREF_PATTERN =
            Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+|www\\.[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+|(?<!@)\\b(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?:/[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM_HANDLE_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9._%+-])@([A-Za-z0-9._]{1,30})(?![A-Za-z0-9._-])");

    private static final List<String> AUXILIARY_HOST_SUFFIXES = List.of(
            "instagram.com",
            "facebook.com",
            "youtube.com",
            "youtu.be",
            "x.com",
            "twitter.com",
            "blog.naver.com",
            "cafe.naver.com",
            "smartstore.naver.com",
            "booking.com",
            "airbnb.co.kr",
            "airbnb.com",
            "modoo.at",
            "linktr.ee",
            "naver.me",
            "tistory.com");

    private TourApiHomepageNormalizer() {}

    /**
     * 원본 홈페이지 텍스트 데이터를 정규화하여 단일 대표 {@code URL}을 반환합니다.
     *
     * @param rawHomepage Tour API 등에서 전달받은 원본 홈페이지 텍스트 데이터. HTML 태그가 포함되어 있을 수 있음
     * @return 정규화된 대표 {@code URL} 문자열. 다음과 같은 조건에 따라 결과가 결정됩니다:
     *         <ul>
     *           <li>{@code rawHomepage}가 {@code null}이면 {@code null} 반환</li>
     *           <li>비어 있는 텍스트(공백만 있는 경우 포함)인 경우 빈 문자열({@code ""}) 반환</li>
     *           <li>대표 웹사이트(Primary) 그룹이 유일한 경우 해당 그룹 내 최적의 {@code URL} 반환</li>
     *           <li>대표 웹사이트 그룹이 여러 개인 경우 모호하여 {@code null} 반환</li>
     *           <li>대표 웹사이트가 없고 보조 채널(Auxiliary) 그룹만 1종류 존재하면 해당 그룹 내 최적의 {@code URL} 반환</li>
     *           <li>대표 웹사이트가 없고 서로 다른 보조 채널 그룹이 혼재하면 {@code null} 반환</li>
     *           <li>유효한 {@code URL} 후보군이 추출되지 않은 경우 {@code null} 반환</li>
     *         </ul>
     */
    public static String normalize(String rawHomepage) {
        if (rawHomepage == null) {
            return null;
        }

        if (!StringUtils.hasText(rawHomepage)) {
            return "";
        }

        String unescaped = HtmlUtils.htmlUnescape(rawHomepage).trim();
        List<HomepageCandidate> candidates = extractCandidates(unescaped);
        if (candidates.isEmpty()) {
            return null;
        }

        Map<String, List<HomepageCandidate>> groupedByHost = groupByHost(candidates);
        List<List<HomepageCandidate>> primaryGroups = groupedByHost.values().stream()
                .filter(group -> group.stream().anyMatch(candidate -> !candidate.auxiliary()))
                .toList();

        if (primaryGroups.size() == 1) {
            return selectBestCandidate(primaryGroups.getFirst()).normalizedUrl();
        }

        if (primaryGroups.size() > 1) {
            return null;
        }

        if (groupedByHost.size() == 1) {
            return selectBestCandidate(groupedByHost.values().iterator().next()).normalizedUrl();
        }

        return null;
    }

    /**
     * 입력 텍스트에서 가능한 모든 {@code URL} 후보군을 추출하고 중복을 제거합니다.
     *
     * <p>현재 버전에서는 다음 3가지 소스에서 후보군을 순차적으로 탐색하여 추출합니다:
     * <ol>
     *   <li>HTML 태그의 {@code href} 속성값</li>
     *   <li>HTML 태그 및 {@code Instagram Handle}을 제거한 일반 텍스트 영역 내 {@code URL} 패턴</li>
     *   <li>HTML 태그를 제거한 일반 텍스트 영역 내 {@code Instagram Handle}({@code @username})을 변환한 Instagram {@code URL}</li>
     * </ol>
     *
     * @param text HTML Unescape 처리가 완료된 입력 텍스트
     * @return 중복이 제거된 {@link HomepageCandidate} 목록
     */
    private static List<HomepageCandidate> extractCandidates(String text) {
        List<HomepageCandidate> candidates = new ArrayList<>();
        int order = 0;

        Matcher hrefMatcher = HREF_PATTERN.matcher(text);
        while (hrefMatcher.find()) {
            HomepageCandidate candidate = createUrlCandidate(hrefMatcher.group(1), true, order++);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        String textWithoutTags = stripHtmlTags(text);
        String textWithoutHandles =
                INSTAGRAM_HANDLE_PATTERN.matcher(textWithoutTags).replaceAll(" ");
        Matcher urlMatcher = URL_PATTERN.matcher(textWithoutHandles);
        while (urlMatcher.find()) {
            HomepageCandidate candidate = createUrlCandidate(urlMatcher.group(), false, order++);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        Matcher handleMatcher = INSTAGRAM_HANDLE_PATTERN.matcher(textWithoutTags);
        while (handleMatcher.find()) {
            String handle = handleMatcher.group(1);
            String instagramUrl = "https://www.instagram.com/" + handle;
            HomepageCandidate candidate = createUrlCandidate(instagramUrl, false, order++);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        return deduplicate(candidates);
    }

    /**
     * 추출한 문자열 토큰을 기반으로 {@link HomepageCandidate} 객체를 생성합니다.
     *
     * <p>현재 버전에서는 다음 단계를 거쳐 후보군을 생성합니다:
     * <ul>
     *   <li>문자열 토큰 내 공백 제거 및 끝자리 특수문자({@code .}, {@code ,}, {@code )}, {@code ]}) 제거</li>
     *   <li>{@code Scheme}(http://, https://)이 없는 경우 기본적으로 {@code https://} 추가</li>
     *   <li>{@link URI} 파싱 검증 및 {@code Host} 확인</li>
     *   <li>{@code Host} 명칭을 소문자로 통일하고 {@code www.} 접두사를 제거하여 그룹화 및 보조 채널 여부 판단</li>
     * </ul>
     *
     * @param rawUrl 추출된 원본 {@code URL} 토큰
     * @param fromHref HTML {@code href} 속성에서 추출되었는지 여부
     * @param order 전체 텍스트 내에서 발견된 순서
     * @return 유효한 URL인 경우 생성된 {@link HomepageCandidate} 객체, 유효하지 않으면 {@code null}
     */
    private static HomepageCandidate createUrlCandidate(String rawUrl, boolean fromHref, int order) {
        String cleaned = cleanUrlToken(rawUrl);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }

        String withScheme = hasScheme(cleaned) ? cleaned : "https://" + cleaned;
        URI uri = parseUri(withScheme);
        if (uri == null || !StringUtils.hasText(uri.getHost())) {
            return null;
        }

        String normalizedUrl = normalizeUri(uri);
        URI normalizedUri = parseUri(normalizedUrl);
        if (normalizedUri == null || !StringUtils.hasText(normalizedUri.getHost())) {
            return null;
        }

        String groupingHost = normalizeHost(normalizedUri.getHost());
        return new HomepageCandidate(normalizedUrl, groupingHost, isAuxiliaryHost(groupingHost), fromHref, order);
    }

    /**
     * 추출된 {@code URL} 후보군 목록에서 프로토콜을 제외한 동일 주소에 대한 중복을 제거합니다.
     *
     * <p>중복 판단 시 {@code http://}를 {@code https://}로 대체하여 비교하며,
     * 동일한 {@code URL}이 존재할 경우 HTML {@code href} 속성으로부터 추출된 후보군({@code fromHref = true})에 우선권을 부여합니다.
     *
     * @param candidates 중복을 검사할 {@link HomepageCandidate} 목록
     * @return 중복이 제거된 {@link HomepageCandidate} 목록
     */
    private static List<HomepageCandidate> deduplicate(List<HomepageCandidate> candidates) {
        Map<String, HomepageCandidate> deduped = new LinkedHashMap<>();
        for (HomepageCandidate candidate : candidates) {
            String key = candidate.normalizedUrl().replaceFirst("^http://", "https://");
            HomepageCandidate existing = deduped.get(key);
            if (existing == null || (!existing.fromHref() && candidate.fromHref())) {
                deduped.put(key, candidate);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    /**
     * {@code URL} 후보군 목록을 {@code Host} 기준으로 그룹화합니다.
     *
     * @param candidates 그룹화할 {@link HomepageCandidate} 목록
     * @return {@code Host}를 Key로 하고, 해당 {@code Host}에 속한 {@link HomepageCandidate} 리스트를 Value로 가지는 {@link Map}
     */
    private static Map<String, List<HomepageCandidate>> groupByHost(List<HomepageCandidate> candidates) {
        Map<String, List<HomepageCandidate>> grouped = new LinkedHashMap<>();
        for (HomepageCandidate candidate : candidates) {
            grouped.computeIfAbsent(candidate.host(), ignored -> new ArrayList<>())
                    .add(candidate);
        }
        return grouped;
    }

    /**
     * 동일한 {@code Host} 그룹 내에서 대표로 사용하기에 가장 적합한 {@link HomepageCandidate}를 하나 선택합니다.
     *
     * <p>우선순위 결정 규칙은 다음과 같습니다:
     * <ol>
     *   <li>HTML {@code href} 속성에서 추출된 후보군({@code fromHref = true}) 우선</li>
     *   <li>{@code URL Path}의 길이가 더 긴 후보군 우선 (구체적인 경로 선호)</li>
     *   <li>텍스트 내에서 먼저 발견된 순서({@code order}가 작은 것) 우선</li>
     * </ol>
     *
     * @param candidates 동일 호스트 내의 {@link HomepageCandidate} 목록
     * @return 가장 우선순위가 높은 {@link HomepageCandidate}
     */
    private static HomepageCandidate selectBestCandidate(List<HomepageCandidate> candidates) {
        return candidates.stream()
                .min(Comparator.comparing(HomepageCandidate::fromHref)
                        .reversed()
                        .thenComparing(Comparator.comparingInt(TourApiHomepageNormalizer::pathLength)
                                .reversed())
                        .thenComparingInt(HomepageCandidate::order))
                .orElseThrow();
    }

    private static int pathLength(HomepageCandidate candidate) {
        URI uri = URI.create(candidate.normalizedUrl());
        String rawPath = uri.getRawPath();
        return rawPath == null ? 0 : rawPath.length();
    }

    private static String normalizeUri(URI uri) {
        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        String fragment = uri.getRawFragment();

        StringBuilder normalized = new StringBuilder();
        normalized.append(scheme).append("://").append(host);
        if (port != -1) {
            normalized.append(":").append(port);
        }
        if (path != null) {
            normalized.append(path);
        }
        if (query != null) {
            normalized.append("?").append(query);
        }
        if (fragment != null) {
            normalized.append("#").append(fragment);
        }
        return normalized.toString();
    }

    private static String cleanUrlToken(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("\\s+", "");
        while (cleaned.endsWith(".") || cleaned.endsWith(",") || cleaned.endsWith(")") || cleaned.endsWith("]")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static String stripHtmlTags(String value) {
        return value.replaceAll("<[^>]*>", " ");
    }

    private static boolean hasScheme(String value) {
        return value.regionMatches(true, 0, "http://", 0, "http://".length())
                || value.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host.toLowerCase();
        return normalized.startsWith("www.") ? normalized.substring("www.".length()) : normalized;
    }

    /**
     * 주어진 {@code Host}가 보조 SNS 채널 또는 외부 플랫폼 도메인({@code Auxiliary Host})인지 확인합니다.
     *
     * <p>{@link #AUXILIARY_HOST_SUFFIXES}에 정의된 도메인과 정확히 일치하거나,
     * 해당 도메인들을 서브도메인으로 포함하고 있는 경우 보조 채널로 분류합니다.
     *
     * @param host 검사할 소문자 및 {@code www.}이 제거된 {@code Host}
     * @return 보조 SNS 또는 외부 플랫폼 채널인 경우 {@code true}, 그렇지 않으면 {@code false}
     */
    private static boolean isAuxiliaryHost(String host) {
        return AUXILIARY_HOST_SUFFIXES.stream().anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
    }

    /**
     * 추출 및 정규화된 홈페이지 {@code URL} 정보를 담는 레코드입니다.
     *
     * @param normalizedUrl 스키마와 호스트가 소문자화 되는 등 정규화 처리가 완료된 최종 {@code URL}
     * @param host 중복 제거 및 호스트 기반 그룹화를 위해 {@code www.}이 제외된 소문자화된 호스트명
     * @param auxiliary 보조 SNS 채널 또는 외부 블로그/예약 플랫폼 여부
     * @param fromHref HTML {@code href} 속성에서 추출된 후보군인지 여부
     * @param order 원본 텍스트 내에서 추출된 순서
     */
    private record HomepageCandidate(
            String normalizedUrl, String host, boolean auxiliary, boolean fromHref, int order) {}
}
