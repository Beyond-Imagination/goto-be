package kr.bi.go_to.batch.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.listener.EtlFailureLogger;
import kr.bi.go_to.batch.validation.TourApiPlaceCategoryValidator;
import kr.bi.go_to.model.batch.CategoryResolutionStatus;
import kr.bi.go_to.model.batch.DetailSyncStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TourApiIncrementalItemProcessor 증분 동기화 처리 테스트")
class TourApiIncrementalItemProcessorTest {

    private TourApiIncrementalItemProcessor processor;
    private TourApiPlaceCategoryValidator categoryValidator;

    @BeforeEach
    void setUp() {
        categoryValidator = mock(TourApiPlaceCategoryValidator.class);
        when(categoryValidator.requireActiveLeaf(any(TourApiItemDto.class))).thenReturn("A0101");
        processor = new TourApiIncrementalItemProcessor(mock(EtlFailureLogger.class), categoryValidator);
    }

    @Test
    @DisplayName("showflag=0인 DTO를 process하면 isDeleted=true로 변환한다")
    void marksPlaceDeletedWhenShowflagIsZero() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("0"));

        assertThat(result).isNotNull();
        assertThat(result.place().isDeleted()).isTrue();
        assertThat(result.place().getCategoryCode()).isNull();
        verifyNoInteractions(categoryValidator);
    }

    @Test
    @DisplayName("showflag=1인 DTO를 process하면 isDeleted=false로 변환한다")
    void restoresPlaceWhenShowflagIsOne() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result).isNotNull();
        assertThat(result.place().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("증분 base step은 detail API를 호출하지 않고 quota가 적용되는 detail step에 PENDING 상태를 인계한다")
    void defersDetailCallsToQuotaBoundDetailStep() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result.place().getCategoryResolutionStatus()).isEqualTo(CategoryResolutionStatus.RESOLVED);
        assertThat(result.place().getDetailCommonStatus()).isEqualTo(DetailSyncStatus.PENDING);
        assertThat(result.place().getDetailWithTourStatus()).isEqualTo(DetailSyncStatus.PENDING);
        assertThat(result.place().getDetailIntroStatus()).isEqualTo(DetailSyncStatus.PENDING);
        assertThat(result.detailCommonSynced()).isFalse();
        assertThat(result.detailWithTourSynced()).isFalse();
        assertThat(result.detailIntroSynced()).isFalse();
    }

    @Test
    @DisplayName("category가 없는 증분 item은 skip하지 않고 PENDING으로 저장해 detail step의 복구 대상으로 남긴다")
    void keepsMissingCategoryPendingForRecoveryInTheDetailStep() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("1", null));

        assertThat(result).isNotNull();
        assertThat(result.place().getCategoryCode()).isNull();
        assertThat(result.place().getCategoryResolutionStatus()).isEqualTo(CategoryResolutionStatus.PENDING);
        verifyNoInteractions(categoryValidator);
    }

    @Test
    @DisplayName("증분 base 결과는 detail endpoint 상태를 모두 PENDING으로 유지한다")
    void keepsEveryDetailEndpointPendingForTheDetailStep() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result.place().getDetailCommonStatus()).isEqualTo(DetailSyncStatus.PENDING);
        assertThat(result.place().getDetailWithTourStatus()).isEqualTo(DetailSyncStatus.PENDING);
        assertThat(result.place().getDetailIntroStatus()).isEqualTo(DetailSyncStatus.PENDING);
    }

    @Test
    @DisplayName("증분 base 결과는 lazy detail fetch 전 overview와 homepage를 덮어쓰지 않는다")
    void leavesDetailTextAbsentUntilTheDetailStep() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result.place().getOverview()).isNull();
        assertThat(result.place().getHomepage()).isNull();
    }

    @Test
    @DisplayName("증분 base 처리 결과는 상세 동기화 완료를 선반영하지 않는다")
    void doesNotPredeclareAnyDetailEndpointAsSynchronized() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result.detailCommonSynced()).isFalse();
        assertThat(result.detailWithTourSynced()).isFalse();
        assertThat(result.detailIntroSynced()).isFalse();
        assertThat(result.place().isDetailCommonSynced()).isFalse();
        assertThat(result.place().isDetailWithTourSynced()).isFalse();
        assertThat(result.place().isDetailIntroSynced()).isFalse();
    }

    private TourApiItemDto createDto(String showflag) {
        return createDto(showflag, "A0101");
    }

    private TourApiItemDto createDto(String showflag, String categoryCode) {
        // 공개 상태 값은 0이면 삭제·비공개, 1이면 공개를 뜻한다.
        return new TourApiItemDto(
                "12345",
                "12",
                "Test Place",
                "Seoul",
                "Gangnam",
                "127.0",
                "37.0",
                null,
                null,
                categoryCode,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                showflag);
    }
}
