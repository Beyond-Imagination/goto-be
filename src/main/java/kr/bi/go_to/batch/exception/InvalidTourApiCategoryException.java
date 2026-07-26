package kr.bi.go_to.batch.exception;

import lombok.Getter;

@Getter
public class InvalidTourApiCategoryException extends RuntimeException {

    private final InvalidTourApiCategoryReason reason;
    private final String contentId;
    private final String categoryCode;

    public InvalidTourApiCategoryException(InvalidTourApiCategoryReason reason, String contentId, String categoryCode) {
        super("[" + reason.name() + "] Invalid current Tour API category" + ", contentId=" + contentId
                + ", categoryCode=" + categoryCode);
        this.reason = reason;
        this.contentId = contentId;
        this.categoryCode = categoryCode;
    }
}
