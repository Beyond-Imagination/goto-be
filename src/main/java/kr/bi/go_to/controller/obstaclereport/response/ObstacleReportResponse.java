package kr.bi.go_to.controller.obstaclereport.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleReportStatus;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;

@Schema(name = "ObstacleReportResponse", description = "장애물 제보 상세 응답")
public record ObstacleReportResponse(
        @Schema(description = "제보 ID") Long id,
        @Schema(description = "위도") double lat,
        @Schema(description = "경도") double lng,
        @Schema(description = "장애물 유형") ObstacleIssueType issueType,
        @Schema(description = "심각도") ObstacleSeverity severity,
        @Schema(description = "영향받는 이동조건 유형") Set<MobilityType> affectedMobilityTypes,
        @Schema(description = "사진 URL 목록") List<String> photoUrls,
        @Schema(description = "상태") ObstacleReportStatus status,
        @Schema(description = "오래되어 확인이 필요한지 여부 (조회 시점 파생 값)") boolean stale,
        @Schema(description = "확인(아직 있어요)한 서로 다른 회원 수") int confirmedCount,
        @Schema(description = "제보 시각") Instant createdAt,
        @Schema(description = "마지막 확인 시각") Instant lastConfirmedAt) {

    public static ObstacleReportResponse from(ObstacleReport report, Instant now) {
        return new ObstacleReportResponse(
                report.getId(),
                report.getLocationPoint().getY(),
                report.getLocationPoint().getX(),
                report.getIssueType(),
                report.getSeverity(),
                Set.copyOf(report.getAffectedMobilityTypes()),
                List.copyOf(report.getPhotoUrls()),
                report.getStatus(),
                report.isStale(now),
                report.getConfirmedCount(),
                report.getCreatedAt(),
                report.getLastConfirmedAt());
    }
}
