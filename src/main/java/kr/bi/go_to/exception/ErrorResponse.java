package kr.bi.go_to.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ErrorResponse", description = "표준 에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 코드", example = "HELP_REQUEST_NOT_FOUND") String errorCode,
        @Schema(description = "사용자에게 전달할 에러 메시지", example = "도움 요청을 찾을 수 없습니다.") String errorMessage) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getErrorMessage());
    }

    public static ErrorResponse of(String errorCode, String errorMessage) {
        return new ErrorResponse(errorCode, errorMessage);
    }
}
