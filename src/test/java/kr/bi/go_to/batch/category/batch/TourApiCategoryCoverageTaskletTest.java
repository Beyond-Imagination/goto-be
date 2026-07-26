package kr.bi.go_to.batch.category.batch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.bi.go_to.batch.category.exception.TourApiCategorySnapshotException;
import kr.bi.go_to.batch.category.repository.TourApiCategoryRepository;
import kr.bi.go_to.batch.category.repository.TourApiCategoryRepository.TourApiCategoryCoverage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TourApiCategoryCoverageTaskletTest {

    private final TourApiCategoryRepository repository = mock(TourApiCategoryRepository.class);
    private final TourApiCategoryCoverageTasklet tasklet = new TourApiCategoryCoverageTasklet(repository);

    @Test
    @DisplayName("모든 출처의 coverage가 안전하면 장소 수집 차단 없이 완료한다")
    void completesWhenEverySourceCoverageIsSafe() {
        when(repository.coverageBySource())
                .thenReturn(List.of(new TourApiCategoryCoverage("TOUR_API", 2, 1, 1, 0, 0, 0, 0, 0, 0)));

        assertThatCode(() -> tasklet.execute(null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("출처별 orphan·inactive·non-leaf·손상 계층·non-Tour 값 중 하나라도 있으면 수집을 차단한다")
    void blocksWhenAnyUnsafeCoverageMetricExists() {
        when(repository.coverageBySource())
                .thenReturn(List.of(
                        new TourApiCategoryCoverage("TOUR_API", 4, 0, 0, 0, 1, 1, 1, 1, 0),
                        new TourApiCategoryCoverage("USER", 1, 1, 0, 0, 0, 0, 0, 0, 1)));

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("TOUR_API")
                .hasMessageContaining("USER");
    }
}
