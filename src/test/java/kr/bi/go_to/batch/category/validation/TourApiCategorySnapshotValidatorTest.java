package kr.bi.go_to.batch.category.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kr.bi.go_to.batch.category.dto.TourApiCategoryItem;
import kr.bi.go_to.batch.category.dto.TourApiCategoryPage;
import kr.bi.go_to.batch.category.exception.TourApiCategorySnapshotException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TourApiCategorySnapshotValidatorTest {

    private final TourApiCategorySnapshotValidator validator = new TourApiCategorySnapshotValidator();

    @Test
    @DisplayName("코드 접두사를 추론하지 않고 반환 튜플로 대·중·소분류 계층을 구성한다")
    void buildsNormalizedHierarchyWithoutInferringPrefixes() {
        var snapshot = validator.validate(List.of(page(1, 10, 1, item("LARGE", "MIDDLE", "LEAF"))));

        assertThat(snapshot.categories())
                .extracting(category -> category.code() + ":" + category.parentCode() + ":" + category.depth())
                .containsExactly("LARGE:null:1", "MIDDLE:LARGE:2", "LEAF:MIDDLE:3");
    }

    @Test
    @DisplayName("페이지 조회 중 전체 건수가 바뀌면 스냅샷을 거부한다")
    void rejectsChangingTotalCount() {
        assertThatThrownBy(() -> validator.validate(
                        List.of(page(1, 1, 2, item("L1", "M1", "S1")), page(2, 1, 3, item("L2", "M2", "S2")))))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("totalCount changed");
    }

    @Test
    @DisplayName("전체 건수가 0인 빈 스냅샷을 거부한다")
    void rejectsZeroCountAndEmptySnapshot() {
        assertThatThrownBy(() -> validator.validate(List.of(page(1, 1, 0))))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("페이지 번호 오류와 조기 빈 페이지 및 조기 짧은 페이지를 거부한다")
    void rejectsWrongPageAndEarlyEmptyOrShortPage() {
        assertThatThrownBy(() -> validator.validate(List.of(page(2, 1, 1, item("L1", "M1", "S1")))))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("Wrong or repeated");
        assertThatThrownBy(() -> validator.validate(List.of(page(1, 2, 2))))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("early empty");
        assertThatThrownBy(() -> validator.validate(List.of(page(1, 2, 2, item("L1", "M1", "S1")))))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("early short");
    }

    @Test
    @DisplayName("불완전한 분류 튜플과 충돌하는 부모 정의를 거부한다")
    void rejectsIncompleteTupleAndConflictingParent() {
        assertThatThrownBy(() -> validator.validate(
                        List.of(page(1, 1, 1, new TourApiCategoryItem("L1", "large", "M1", "middle", "S1", " ")))))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("incomplete");
        assertThatThrownBy(() ->
                        validator.validate(List.of(page(1, 2, 2, item("L1", "M1", "SAME"), item("L2", "M2", "SAME")))))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("Conflicting taxonomy definition");
    }

    private TourApiCategoryPage page(int pageNo, int numOfRows, int totalCount, TourApiCategoryItem... items) {
        return new TourApiCategoryPage(pageNo, numOfRows, totalCount, List.of(items));
    }

    private TourApiCategoryItem item(String large, String middle, String small) {
        return new TourApiCategoryItem(large, large + " name", middle, middle + " name", small, small + " name");
    }
}
