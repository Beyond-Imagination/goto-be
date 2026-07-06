package kr.bi.go_to.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 400 BAD_REQUEST
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    CANNOT_ACCEPT_OWN_HELP_REQUEST(HttpStatus.BAD_REQUEST, "자신의 도움 요청은 수락할 수 없습니다."),
    CANNOT_REJECT_OWN_HELP_REQUEST(HttpStatus.BAD_REQUEST, "자신의 도움 요청은 거절할 수 없습니다."),

    // 401 UNAUTHORIZED
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    UNKNOWN_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "알 수 없는 리프레시 토큰입니다."),
    EXPIRED_OR_REVOKED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "만료되었거나 폐기된 리프레시 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    MEMBER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."),

    // 403 FORBIDDEN
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ONLY_REQUESTER_CAN_CANCEL(HttpStatus.FORBIDDEN, "요청자만 도움 요청을 취소할 수 있습니다."),

    // 404 NOT_FOUND
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    HELP_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "도움 요청을 찾을 수 없습니다."),

    // 409 CONFLICT
    HELP_REQUEST_EXPIRED(HttpStatus.CONFLICT, "도움 요청이 만료되었습니다."),
    HELP_REQUEST_NOT_OPEN(HttpStatus.CONFLICT, "열려 있는 도움 요청이 아닙니다."),
    HELP_REQUEST_ALREADY_REJECTED(HttpStatus.CONFLICT, "이미 거절한 도움 요청입니다."),
    HELP_REQUEST_NOT_ACCEPTED(HttpStatus.CONFLICT, "수락된 도움 요청만 완료할 수 있습니다."),
    HELP_REQUEST_CANNOT_BE_CANCELED(HttpStatus.CONFLICT, "취소할 수 없는 도움 요청입니다."),

    // 500 INTERNAL_SERVER_ERROR
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String errorMessage;

    ErrorCode(HttpStatus httpStatus, String errorMessage) {
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
