package kr.bi.go_to.service.member;

/**
 * 커서가 분류별 위치를 담을 때 쓰는 키.
 * 내 제보 기록은 분류 3종을, 내가 확인한 리포트는 CONFIRMATION 하나만 쓴다.
 */
public enum MyReportCursorKey {
    OBSTACLE,
    PLACE,
    FACILITY,
    CONFIRMATION,
}
