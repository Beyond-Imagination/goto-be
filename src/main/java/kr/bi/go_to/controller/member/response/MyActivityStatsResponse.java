package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MyActivityStatsResponse", description = "내 정보 홈 상단의 활동 통계 3종")
public record MyActivityStatsResponse(
        @Schema(description = "내가 작성한 장애물 제보 수", example = "12") long reportCount,
        @Schema(description = "내 제보를 확인해 준 사람 수의 총합 (제보별 확인 수 합계)", example = "48") long helpedPeopleCount,
        @Schema(description = "내가 확인해 준 제보 중 해결된 건수", example = "3") long resolvedConfirmationCount) {}
