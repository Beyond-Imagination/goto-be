package kr.bi.go_to.model.placereport;

/**
 * 장소의 개별 편의시설 상태.
 *
 * <p>UNKNOWN은 저장하지 않는다(모른다는 응답은 항목 자체를 보내지 않는 것으로 표현한다).
 * place_bf_info의 BfItem.isAvailable이 null/true/false 3값인 것과 같은 이유로,
 * "확인 못 함"과 "없음"은 구분되어야 한다.
 */
public enum PlaceFacilityStatus {
    /** 있고 정상적으로 쓸 수 있었다. */
    AVAILABLE,
    /** 아예 없었다. */
    UNAVAILABLE,
    /** 있지만 고장·점검 등으로 쓸 수 없었다. */
    BROKEN,
}
