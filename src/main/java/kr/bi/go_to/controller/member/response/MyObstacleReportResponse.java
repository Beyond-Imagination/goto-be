package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleReportStatus;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;

@Schema(name = "MyObstacleReportResponse", description = "내 제보 기록 · 내가 확인한 리포트 목록의 항목")
public record MyObstacleReportResponse(
        @Schema(description = "제보 식별자", example = "1247") Long id,
        @Schema(description = "장애물 유형", example = "SIDEWALK_DAMAGE") ObstacleIssueType issueType,
        @Schema(description = "심각도", example = "CAUTION") ObstacleSeverity severity,
        @Schema(description = "제보 상태", example = "ACTIVE") ObstacleReportStatus status,
        @Schema(description = "오래된 정보 여부 (마지막 확인 이후 30일 경과, 조회 시점 파생값)", example = "false") boolean stale,
        @Schema(description = "영향받는 이동 조건", example = "[\"WHEELCHAIR\"]") List<MobilityType> affectedMobilityTypes,
        @Schema(description = "위도", example = "37.5665") double latitude,
        @Schema(description = "경도", example = "126.978") double longitude,
        // TODO(GOTO-110): 역지오코딩 또는 Place 연관을 도입해 실제 주소를 채운다.
        //  현재는 ObstacleReport에 좌표만 있어 항상 null이고, FE는 "위치 37.56650, 126.97800" 형태로 대체 표기한다.
        //  디자인(내 정보 03·05)의 "서울시 마포구 월드컵로 23길" 표기를 살리려면 이 필드가 채워져야 한다.
        @Schema(description = "표시용 주소. 역지오코딩 미도입 상태라 현재는 항상 null이며, FE는 좌표로 대체 표기한다.", example = "null") String address,
        @Schema(description = "첨부 사진 URL 목록", example = "[]") List<String> photoUrls,
        @Schema(description = "이 제보를 확인해 준 사람 수", example = "5") int confirmedCount,
        @Schema(description = "마지막 확인 시각", example = "2026-08-20T04:15:30Z") Instant lastConfirmedAt,
        @Schema(description = "제보 작성 시각", example = "2026-08-12T04:15:30Z") Instant createdAt) {

    public static MyObstacleReportResponse from(ObstacleReport report, Instant now) {
        return new MyObstacleReportResponse(
                report.getId(),
                report.getIssueType(),
                report.getSeverity(),
                report.getStatus(),
                report.isStale(now),
                List.copyOf(report.getAffectedMobilityTypes()),
                report.getLocationPoint().getY(),
                report.getLocationPoint().getX(),
                // TODO(GOTO-110): 역지오코딩 도입 시 좌표 → 주소 변환 결과를 넣는다.
                null,
                // 지연 로딩 컬렉션을 그대로 담으면 트랜잭션 종료 후 직렬화에서 LazyInitializationException이 난다.
                List.copyOf(report.getPhotoUrls()),
                report.getConfirmedCount(),
                report.getLastConfirmedAt(),
                report.getCreatedAt());
    }
}
