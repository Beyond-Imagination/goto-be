package kr.bi.go_to.batch.exception;

import lombok.Getter;

@Getter
public class HomepageParsingException extends RuntimeException {
    private final HomepageParsingErrorType errorType;

    public HomepageParsingException(HomepageParsingErrorType errorType, Object... args) {
        super(String.format(errorType.getMessageTemplate(), args));
        this.errorType = errorType;
    }
}
