package kr.bi.go_to.batch.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import kr.bi.go_to.batch.client.TourApiClient;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.listener.EtlFailureLogger;
import kr.bi.go_to.batch.mapper.TourApiHomepageNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

@DisplayName("TourApiIncrementalItemProcessor 증분 동기화 처리 테스트")
class TourApiIncrementalItemProcessorTest {

    private TourApiIncrementalItemProcessor processor;
    private TourApiClient tourApiClient;

    @BeforeEach
    void setUp() {
        tourApiClient = mock(TourApiClient.class);
        when(tourApiClient.fetchDetail(anyString(), anyString(), nullable(String.class)))
                .thenReturn(null);
        when(tourApiClient.extractFieldOrEmpty(any(JsonNode.class), anyString()))
                .thenReturn("");

        processor = new TourApiIncrementalItemProcessor(
                mock(EtlFailureLogger.class), tourApiClient, new TourApiHomepageNormalizer());
    }

    @Test
    @DisplayName("showflag=0인 DTO를 process하면 isDeleted=true로 변환한다")
    void marksPlaceDeletedWhenShowflagIsZero() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("0"));

        assertThat(result).isNotNull();
        assertThat(result.place().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("showflag=1인 DTO를 process하면 isDeleted=false로 변환한다")
    void restoresPlaceWhenShowflagIsOne() throws Exception {
        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result).isNotNull();
        assertThat(result.place().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("세 detail API가 모두 성공하면 process 결과를 상세 보강 완료 상태로 표시한다")
    void marksDetailCompleteOnlyWhenAllDetailApisSucceed() throws Exception {
        JsonNode common2 = mock(JsonNode.class);
        JsonNode withTour2 = mock(JsonNode.class);
        JsonNode intro2 = mock(JsonNode.class);
        when(tourApiClient.fetchDetail(eq("detailCommon2"), anyString(), nullable(String.class)))
                .thenReturn(common2);
        when(tourApiClient.fetchDetail(eq("detailWithTour2"), anyString(), nullable(String.class)))
                .thenReturn(withTour2);
        when(tourApiClient.fetchDetail(eq("detailIntro2"), anyString(), nullable(String.class)))
                .thenReturn(intro2);

        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result.detailCommonSynced()).isTrue();
        assertThat(result.detailWithTourSynced()).isTrue();
        assertThat(result.detailIntroSynced()).isTrue();
        assertThat(result.place().isDetailCommonSynced()).isTrue();
        assertThat(result.place().isDetailWithTourSynced()).isTrue();
        assertThat(result.place().isDetailIntroSynced()).isTrue();
    }

    @Test
    @DisplayName("detailCommon2는 성공했지만 overview/homepage가 없으면 process 결과를 빈 문자열로 매핑한다")
    void mapsMissingCommonDetailFieldsToEmptyStringsWhenDetailCommonSucceeds() throws Exception {
        JsonNode common2 = mock(JsonNode.class);
        when(tourApiClient.fetchDetail(eq("detailCommon2"), anyString(), nullable(String.class)))
                .thenReturn(common2);
        when(tourApiClient.extractFieldOrEmpty(common2, "overview")).thenReturn("");
        when(tourApiClient.extractFieldOrEmpty(common2, "homepage")).thenReturn("");

        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result.detailCommonSynced()).isTrue();
        assertThat(result.place().getOverview()).isEmpty();
        assertThat(result.place().getHomepage()).isEmpty();
    }

    @Test
    @DisplayName("detail API 중 하나라도 실패하면 process 결과 상세 보강 미완료 상태를 유지한다")
    void leavesDetailIncompleteWhenAnyDetailApiFails() throws Exception {
        JsonNode common2 = mock(JsonNode.class);
        JsonNode intro2 = mock(JsonNode.class);
        when(tourApiClient.fetchDetail(eq("detailCommon2"), anyString(), nullable(String.class)))
                .thenReturn(common2);
        when(tourApiClient.fetchDetail(eq("detailWithTour2"), anyString(), nullable(String.class)))
                .thenReturn(null);
        when(tourApiClient.fetchDetail(eq("detailIntro2"), anyString(), nullable(String.class)))
                .thenReturn(intro2);

        PlaceProcessingResult result = processor.process(createDto("1"));

        assertThat(result.detailCommonSynced()).isTrue();
        assertThat(result.detailWithTourSynced()).isFalse();
        assertThat(result.detailIntroSynced()).isTrue();
        assertThat(result.place().isDetailCommonSynced()).isTrue();
        assertThat(result.place().isDetailWithTourSynced()).isFalse();
        assertThat(result.place().isDetailIntroSynced()).isTrue();
    }

    private TourApiItemDto createDto(String showflag) {
        // 한국관광공사 증분 API의 showflag는 공개/삭제 상태를 나타낸다.
        // 0: 삭제 또는 비공개, 1: 공개 중인 데이터.
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
                "A0101",
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
