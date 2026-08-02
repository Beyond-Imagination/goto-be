package kr.bi.go_to.model.obstaclereport;

/**
 * 선언 순서가 곧 심각도 순위다 (IMPASSABLE이 가장 심각) — 클러스터 집계에서 최고 심각도를 ordinal로 비교한다.
 */
public enum ObstacleSeverity {
    IMPASSABLE,
    CAUTION,
    INFO,
}
