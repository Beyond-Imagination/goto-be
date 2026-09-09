package kr.bi.go_to.model.placereport;

/**
 * 장소 전반의 이용 난이도. 장애물 제보의 severity와 같은 역할이지만,
 * 지점이 아니라 장소 단위 경험을 표현한다.
 */
public enum PlaceAccessStatus {
    /** 이용 편했어요 — 별다른 제약 없이 이용했다. */
    ACCESSIBLE,
    /** 일부 불편했어요 — 이용은 했지만 제약이 있었다. */
    PARTIALLY_ACCESSIBLE,
    /** 이용 어려웠어요 — 사실상 이용하지 못했다. */
    INACCESSIBLE,
}
