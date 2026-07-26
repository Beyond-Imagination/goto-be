package kr.bi.go_to.batch.exception;

/** Tour API 장소 조회 중 발생하며 건너뛸 수 없는 공급자·전송 오류다. */
public class TourApiInfrastructureException extends RuntimeException {

    public TourApiInfrastructureException(String message) {
        super(message);
    }

    public TourApiInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
