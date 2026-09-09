package kr.bi.go_to.enums;

/**
 * 내 제보 기록의 분류. FE 내 정보 03 화면의 「장애물 · 장소 · 시설」 필터 칩과 1:1로 맞춘다.
 */
public enum MyReportKind {
    /** 길 위 장애물 제보 (ObstacleReport) */
    OBSTACLE,
    /** 장소 단위 상태 제보 (PlaceStateReport) */
    PLACE,
    /** 실내 시설 상태 제보 (Report) */
    FACILITY,
}
