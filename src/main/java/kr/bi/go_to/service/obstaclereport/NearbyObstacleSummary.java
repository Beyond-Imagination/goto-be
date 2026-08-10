package kr.bi.go_to.service.obstaclereport;

/**
 * 반경 내 {@code ObstacleReport}(ACTIVE만)를 severity(우회권장/주의/안전)와 STALE(확인필요) 두 독립된 축으로
 * 집계한 결과. 하나의 리포트가 두 카운트에 동시에 잡힐 수 있다(합이 전체 리포트 수와 다를 수 있음).
 */
public record NearbyObstacleSummary(
        int detourRecommendedCount, int cautionCount, int safeCount, int needsConfirmationCount) {}
