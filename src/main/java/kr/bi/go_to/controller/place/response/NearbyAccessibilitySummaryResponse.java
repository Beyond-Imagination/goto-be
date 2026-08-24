package kr.bi.go_to.controller.place.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * "내 주변" 반경 내 {@code ObstacleReport}(ACTIVE 상태만)를 집계한 결과.
 * severity 기반 카운트(우회권장/주의/안전)와 STALE 기반 카운트(확인필요)는 서로 독립된 축이라,
 * 하나의 리포트가 두 카운트에 동시에 잡힐 수 있다 — 합이 전체 리포트 수와 다를 수 있음(ADR-0005 참고).
 */
@Schema(name = "NearbyAccessibilitySummaryResponse", description = "내 주변 접근성 정보 요약")
public record NearbyAccessibilitySummaryResponse(
        @Schema(description = "우회권장(통행 어려움, severity=IMPASSABLE) 리포트 수") int detourRecommendedCount,
        @Schema(description = "주의(severity=CAUTION) 리포트 수") int cautionCount,
        @Schema(description = "안전(severity=INFO) 리포트 수") int safeCount,
        @Schema(description = "확인필요(오래됨, 마지막 확인으로부터 30일 경과) 리포트 수") int needsConfirmationCount) {}
