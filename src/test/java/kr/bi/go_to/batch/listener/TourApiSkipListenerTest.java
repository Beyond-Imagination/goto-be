package kr.bi.go_to.batch.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryException;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TourApiSkipListenerTest {

    @Test
    @DisplayName("잘못된 분류를 process에서 스킵하면 실패 증거를 정확히 한 번 기록한다")
    void invalidCategoryProducesExactlyOneFailureEvidenceWrite() {
        EtlFailureLogger logger = mock(EtlFailureLogger.class);
        TourApiSkipListener listener = new TourApiSkipListener(logger);
        TourApiItemDto item = item();
        InvalidTourApiCategoryException failure = new InvalidTourApiCategoryException(
                InvalidTourApiCategoryReason.UNKNOWN_INACTIVE_OR_NON_LEAF, item.contentid(), item.lclsSystm3());

        listener.onSkipInProcess(item, failure);

        verify(logger, times(1)).logFailure("12345", failure.getMessage());
    }

    @Test
    @DisplayName("실패 증거 저장소 장애는 스킵하지 않고 배치를 실패시킨다")
    void evidenceInfrastructureFailureIsFatal() {
        EtlFailureLogger logger = mock(EtlFailureLogger.class);
        RuntimeException databaseFailure = new RuntimeException("database unavailable");
        doThrow(databaseFailure).when(logger).logFailure("12345", "invalid");
        TourApiSkipListener listener = new TourApiSkipListener(logger);

        assertThatThrownBy(() -> listener.onSkipInProcess(item(), new RuntimeException("invalid")))
                .isSameAs(databaseFailure);
    }

    private TourApiItemDto item() {
        return new TourApiItemDto(
                "12345", "12", "Place", null, null, null, null, "A", "A01", "UNKNOWN", null, null, null, null, null,
                null, null, null, null, null, null, "1");
    }
}
