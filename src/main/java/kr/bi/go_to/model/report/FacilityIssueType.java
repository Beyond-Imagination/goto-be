package kr.bi.go_to.model.report;

/**
 * 실내 편의시설(엘리베이터, 경사로, 손잡이 등) 상태 제보의 이슈 유형.
 *
 * <p>reports.issue_type 컬럼은 VARCHAR(50) 문자열이라 예전 데이터에는 이 목록에 없는 값이 있을 수
 * 있다. 그래서 입력만 이 enum으로 검증하고, 응답은 저장된 문자열을 그대로 내려준다.
 */
public enum FacilityIssueType {
    /** 고장 — 작동하지 않음 */
    BROKEN,
    /** 운영 중지 — 점검·공사 등으로 쓸 수 없음 */
    OUT_OF_SERVICE,
    /** 통행 막힘 — 물건·잠금 등으로 접근할 수 없음 */
    BLOCKED,
    /** 파손 — 손잡이·바닥 등이 깨지거나 흔들림 */
    DAMAGED,
    /** 없어짐 — 지도에 있는데 실제로는 없음 */
    MISSING,
    /** 수리 완료 — 이전 제보가 해결됨 */
    REPAIRED,
    /** 기타 — 위 분류에 없는 문제 */
    OTHER,
}
