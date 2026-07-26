package kr.bi.go_to.batch.sync;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.bi.go_to.batch.client.TourApiCategoryClient;
import kr.bi.go_to.batch.dto.TourApiCategoryItem;
import kr.bi.go_to.batch.dto.TourApiCategoryPage;
import kr.bi.go_to.batch.exception.TourApiCategorySnapshotException;
import kr.bi.go_to.batch.repository.TourApiCategoryRepository;
import kr.bi.go_to.batch.validation.TourApiCategorySnapshotValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TourApiCategorySynchronizerTest {

    private final TourApiCategoryClient client = mock(TourApiCategoryClient.class);
    private final TourApiCategoryRepository repository = mock(TourApiCategoryRepository.class);
    private final TourApiCategorySynchronizer synchronizer =
            new TourApiCategorySynchronizer(client, new TourApiCategorySnapshotValidator(), repository, 1);

    @Test
    @DisplayName("전체 페이지 검증이 실패하면 운영 분류체계를 publish하지 않는다")
    void failedTraversalNeverMutatesLiveTaxonomy() {
        when(client.fetchPage(1, 1)).thenReturn(page(1, 2, item("L1", "M1", "S1")));
        when(client.fetchPage(2, 1)).thenReturn(page(2, 3, item("L2", "M2", "S2")));

        assertThatThrownBy(synchronizer::synchronize)
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("totalCount changed");

        verify(repository, never()).publish(any(), any());
    }

    @Test
    @DisplayName("완전한 분류체계 스냅샷을 검증한 뒤에만 publish한다")
    void publishesOnlyAfterCompleteValidation() {
        when(client.fetchPage(1, 1)).thenReturn(page(1, 2, item("L1", "M1", "S1")));
        when(client.fetchPage(2, 1)).thenReturn(page(2, 2, item("L2", "M2", "S2")));

        synchronizer.synchronize();

        verify(repository).publish(any(), any());
    }

    @Test
    @DisplayName("전체 건수가 0인 빈 스냅샷은 publish 전에 거부한다")
    void rejectsZeroCountSnapshotWithoutPublishing() {
        when(client.fetchPage(1, 1)).thenReturn(new TourApiCategoryPage(1, 1, 0, List.of()));

        assertThatThrownBy(synchronizer::synchronize)
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("must be positive");

        verify(repository, never()).publish(any(), any());
    }

    private TourApiCategoryPage page(int pageNo, int totalCount, TourApiCategoryItem item) {
        return new TourApiCategoryPage(pageNo, 1, totalCount, List.of(item));
    }

    private TourApiCategoryItem item(String large, String middle, String small) {
        return new TourApiCategoryItem(large, large + " name", middle, middle + " name", small, small + " name");
    }
}
