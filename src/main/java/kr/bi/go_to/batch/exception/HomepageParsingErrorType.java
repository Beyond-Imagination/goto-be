package kr.bi.go_to.batch.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HomepageParsingErrorType {
    MULTIPLE_URLS("홈페이지 정보에 여러 개의 URL이 포함되어 있습니다: %s"),
    INVALID_URL_FORMAT("홈페이지에서 추출된 결과가 유효한 URL 형식이 아닙니다: %s (원본: %s)"),
    NO_EXTRACTED_URL("홈페이지 정보에서 유효한 URL을 추출할 수 없습니다: %s");

    private final String messageTemplate;
}
