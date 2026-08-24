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
        @Schema(description = "오래되어 확인이 필요한 리포트 수") int staleReportCount) {

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
                staleReportCount);
    }
}
