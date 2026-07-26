package kr.bi.go_to.batch.category.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.bi.go_to.batch.category.dto.TourApiCategoryNode;
import kr.bi.go_to.batch.category.exception.TourApiCategorySnapshotException;
import kr.bi.go_to.batch.category.repository.TourApiCategoryRepository;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryException;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TourApiPlaceCategoryValidatorTest {

    private final TourApiCategoryRepository repository = mock(TourApiCategoryRepository.class);
    private final TourApiPlaceCategoryValidator validator = new TourApiPlaceCategoryValidator(repository);

    @Test
    @DisplayName("활성 소분류와 정확한 활성 1→2→3 계층을 확인한 뒤 코드를 반환한다")
    void returnsCurrentLeafWhenItIsActiveDepthThree() {
        when(repository.isActiveLeaf("A01010100")).thenReturn(true);
        when(repository.findActiveLeafAncestry("A01010100"))
                .thenReturn(List.of(
                        new TourApiCategoryNode("A", "large", (short) 1),
                        new TourApiCategoryNode("A01", "middle", (short) 2),
                        new TourApiCategoryNode("A01010100", "small", (short) 3)));

        assertThat(validator.requireActiveLeaf(item("A01010100"))).isEqualTo("A01010100");
        verify(repository).findActiveLeafAncestry("A01010100");
    }

    @Test
    @DisplayName("소분류 코드가 없으면 안정적인 스킵 사유로 분류한다")
    void missingLeafHasStableReason() {
        assertThatThrownBy(() -> validator.requireActiveLeaf(item(" ")))
                .isInstanceOfSatisfying(InvalidTourApiCategoryException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(InvalidTourApiCategoryReason.MISSING_CURRENT_LEAF);
                    assertThat(exception.getContentId()).isEqualTo("12345");
                });
    }

    @Test
    @DisplayName("누락·비활성·비소분류 코드는 안정적인 스킵 사유로 분류한다")
    void unknownInactiveOrNonLeafHasStableReason() {
        when(repository.isActiveLeaf("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> validator.requireActiveLeaf(item("UNKNOWN")))
                .isInstanceOfSatisfying(
                        InvalidTourApiCategoryException.class, exception -> assertThat(exception.getReason())
                                .isEqualTo(InvalidTourApiCategoryReason.UNKNOWN_INACTIVE_OR_NON_LEAF));
    }

    @Test
    @DisplayName("저장소 인프라 실패는 스킵 가능한 데이터 오류로 변환하지 않는다")
    void repositoryInfrastructureFailureIsNotConvertedToSkippableDataFailure() {
        IllegalStateException infrastructureFailure = new IllegalStateException("database unavailable");
        when(repository.isActiveLeaf("A01010100")).thenThrow(infrastructureFailure);

        assertThatThrownBy(() -> validator.requireActiveLeaf(item("A01010100"))).isSameAs(infrastructureFailure);
    }

    @Test
    @DisplayName("활성 부모 계층 손상은 스킵 가능한 데이터 오류로 변환하지 않는다")
    void hierarchyCorruptionIsNotConvertedToSkippableDataFailure() {
        when(repository.isActiveLeaf("A01010100")).thenReturn(true);
        TourApiCategorySnapshotException corruption = new TourApiCategorySnapshotException("broken active ancestry");
        when(repository.findActiveLeafAncestry("A01010100")).thenThrow(corruption);

        assertThatThrownBy(() -> validator.requireActiveLeaf(item("A01010100")))
                .isSameAs(corruption)
                .isNotInstanceOf(InvalidTourApiCategoryException.class);
    }

    private TourApiItemDto item(String leaf) {
        return new TourApiItemDto(
                "12345", "12", "Place", null, null, null, null, "A", "A01", leaf, null, null, null, null, null, null,
                null, null, null, null, null, "1");
    }
}
