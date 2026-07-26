package kr.bi.go_to.batch.exception;

/** Non-skippable provider/transport failure while reading Tour API place data. */
public class TourApiInfrastructureException extends RuntimeException {

    public TourApiInfrastructureException(String message) {
        super(message);
    }

    public TourApiInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
