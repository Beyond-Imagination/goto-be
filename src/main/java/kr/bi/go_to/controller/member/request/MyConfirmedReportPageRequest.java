package kr.bi.go_to.controller.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.bi.go_to.model.obstaclereport.ObstacleReportStatus;

@Schema(name = "MyConfirmedReportPageRequest", description = "내가 확인한 리포트 목록 조회 조건")
public record MyConfirmedReportPageRequest(
        @Schema(description = "확인 대상 제보의 상태 필터 (ACTIVE: 아직 있음, RESOLVED: 해결 됨). 없으면 전체", nullable = true)
                ObstacleReportStatus status,
        @Schema(description = "이전 응답의 nextCursor. 없으면 첫 페이지", nullable = true) String cursor,
        @Schema(description = "한 페이지 크기 (1~50, 기본 20)", example = "20") @Min(1) @Max(50) Integer size) {

    private static final int DEFAULT_SIZE = 20;

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
