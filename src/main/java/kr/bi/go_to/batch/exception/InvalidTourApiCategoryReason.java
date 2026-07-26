package kr.bi.go_to.batch.exception;

/** Tour API 장소 분류 검증에서 사용하는 원천 데이터 실패 사유다. */
public enum InvalidTourApiCategoryReason {
    MISSING_CURRENT_LEAF,
    UNKNOWN_INACTIVE_OR_NON_LEAF
}
