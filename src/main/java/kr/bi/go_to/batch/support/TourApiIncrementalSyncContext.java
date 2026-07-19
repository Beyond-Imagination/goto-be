package kr.bi.go_to.batch.support;

public final class TourApiIncrementalSyncContext {

    public static final String JOB_NAME = "tourApiIncrementalSyncJob";
    public static final String BASE_STEP_NAME = "tourApiIncrementalBaseSyncStep";
    public static final String REQUEST_DATE_KEY = "tourApi.incremental.requestDate";
    public static final String TARGET_DATE_KEY = "tourApi.incremental.targetDate";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAIL = "FAIL";

    private TourApiIncrementalSyncContext() {}
}
