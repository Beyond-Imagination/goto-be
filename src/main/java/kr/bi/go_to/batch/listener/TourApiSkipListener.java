package kr.bi.go_to.batch.listener;

import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiSkipListener implements SkipListener<TourApiItemDto, PlaceProcessingResult> {

    private final EtlFailureLogger failureLogger;

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("Skip in read: ", t);
        logFailure(null, t.getMessage());
    }

    @Override
    public void onSkipInWrite(PlaceProcessingResult item, Throwable t) {
        log.error("Skip in write for externalId {}: ", item.place().getExternalId(), t);
        logFailure(item.place().getExternalId(), t.getMessage());
    }

    @Override
    public void onSkipInProcess(TourApiItemDto item, Throwable t) {
        log.error("Skip in process for contentid {}: ", item.contentid(), t);
        logFailure(item.contentid(), t.getMessage());
    }

    private void logFailure(String externalId, String errorMessage) {
        StepContext context = StepSynchronizationManager.getContext();
        if (context == null || context.getJobInstanceId() == null) {
            failureLogger.logFailure(externalId, errorMessage);
            return;
        }
        failureLogger.logFailure(context.getJobInstanceId(), context.getStepName(), externalId, errorMessage);
    }
}
