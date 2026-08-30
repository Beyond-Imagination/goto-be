package kr.bi.go_to.controller.obstaclereport.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;

@Schema(name = "ObstacleReportClusterResponse", description = "장애물 제보 클러스터 요약")
public record ObstacleReportClusterResponse(
        @Schema(description = "클러스터 중심 위도") double centerLat,
        @Schema(description = "클러스터 중심 경도") double centerLng,
        @Schema(description = "리포트 수") int reportCount,
        @Schema(description = "최고 심각도") ObstacleSeverity maxSeverity,
        @Schema(description = "주요 유형 top3") List<IssueTypeCount> topIssueTypes,
        @Schema(description = "최근 제보 시각") Instant latestReportAt,
        @Schema(description = "이 클러스터에 포함된 리포트들이 영향을 주는 이동조건 유형의 합집합") Set<MobilityType> affectedMobilityTypes,
        @Schema(description = "확인된(1명 이상 확인한) 리포트 수") int confirmedReportCount,
        @Schema(description = "해결됨 표시된 리포트 수") int resolvedReportCount,
        @Schema(description = "오래되어 확인이 필요한 리포트 수") int staleReportCount,
        @Schema(
                        description = "이 클러스터에 리포트가 정확히 1건일 때만 채워지는 원본 리포트 ID. 그 외엔 null. "
                                + "가까운 줌은 언클러스터링이라 항상 1건이지만, 먼/중간 줌이라도 밀도가 낮은 지역이면 "
                                + "우연히 1건짜리 클러스터가 되어 채워질 수 있다 — \"가까운 줌 전용\" 신호로 쓰지 말 것")
                Long id,
        @Schema(description = "이 클러스터에 리포트가 정확히 1건일 때만 채워지는 사진 URL 목록. 그 외엔 null. id와 동일한 조건") List<String> photoUrls,
        @Schema(description = "중간 줌 구간에서만 계산되는 최근접 장소/행정동 라벨(\"OO 인근\"). 매칭 실패 시 null") String nearbyPlaceLabel) {

    @Schema(name = "ObstacleReportClusterIssueTypeCount", description = "클러스터 내 장애물 유형별 건수")
    public record IssueTypeCount(
            @Schema(description = "장애물 유형") ObstacleIssueType issueType, @Schema(description = "건수") int count) {}

    public static ObstacleReportClusterResponse from(List<ObstacleReport> reports, Instant now) {
        double centerLat = reports.stream()
                .mapToDouble(r -> r.getLocationPoint().getY())
                .average()
                .orElseThrow();
        double centerLng = reports.stream()
                .mapToDouble(r -> r.getLocationPoint().getX())
                .average()
                .orElseThrow();

        ObstacleSeverity maxSeverity = reports.stream()
                .map(ObstacleReport::getSeverity)
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElseThrow();

        List<IssueTypeCount> topIssueTypes = reports.stream()
                .collect(Collectors.groupingBy(ObstacleReport::getIssueType, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<ObstacleIssueType, Long>comparingByValue().reversed())
                .limit(3)
                .map(entry ->
                        new IssueTypeCount(entry.getKey(), entry.getValue().intValue()))
                .toList();

        Instant latestReportAt = reports.stream()
                .map(ObstacleReport::getCreatedAt)
                .max(Instant::compareTo)
                .orElseThrow();

        Set<MobilityType> affectedMobilityTypes = reports.stream()
                .flatMap(r -> r.getAffectedMobilityTypes().stream())
                .collect(Collectors.toSet());

        int confirmedReportCount =
                (int) reports.stream().filter(r -> r.getConfirmedCount() > 0).count();
        int resolvedReportCount =
                (int) reports.stream().filter(ObstacleReport::isResolved).count();
        int staleReportCount =
                (int) reports.stream().filter(r -> r.isStale(now)).count();

        // 리포트가 정확히 1건인 클러스터일 때만 개별 리포트 상세(id/사진)를 노출한다. 가까운 줌은
        // 언클러스터링이라 항상 여기 해당하지만, 이 조건 자체는 zoom과 무관하다(먼/중간 줌도 밀도가
        // 낮으면 우연히 1건짜리가 될 수 있다) — @Schema 설명 참고.
        boolean isSingleReport = reports.size() == 1;
        Long id = isSingleReport ? reports.get(0).getId() : null;
        // getPhotoUrls()는 지연 로딩 컬렉션을 그대로 반환한다. 트랜잭션/세션이 끝난 뒤(JSON 직렬화 시점)
        // 이 record가 그 프록시를 그대로 들고 있으면 LazyInitializationException이 난다 — List.copyOf로
        // 여기서 즉시 복사해 세션과 무관한 순수 값으로 만든다.
        List<String> photoUrls = isSingleReport ? List.copyOf(reports.get(0).getPhotoUrls()) : null;

        return new ObstacleReportClusterResponse(
                centerLat,
                centerLng,
                reports.size(),
                maxSeverity,
                topIssueTypes,
                latestReportAt,
                affectedMobilityTypes,
                confirmedReportCount,
                resolvedReportCount,
                staleReportCount,
                id,
                photoUrls,
                null);
    }

    /** 중간 줌 구간에서 계산된 최근접 장소 라벨을 붙인 사본을 반환한다. */
    public ObstacleReportClusterResponse withNearbyPlaceLabel(String nearbyPlaceLabel) {
        return new ObstacleReportClusterResponse(
                centerLat,
                centerLng,
                reportCount,
                maxSeverity,
                topIssueTypes,
                latestReportAt,
                affectedMobilityTypes,
                confirmedReportCount,
                resolvedReportCount,
                staleReportCount,
                id,
                photoUrls,
                nearbyPlaceLabel);
    }
}
