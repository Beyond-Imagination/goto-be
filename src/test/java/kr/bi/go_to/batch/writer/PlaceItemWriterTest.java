package kr.bi.go_to.batch.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.List;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.exception.MixedSourceChunkException;
import kr.bi.go_to.batch.mapper.TourApiBfDetailsNormalizer;
import kr.bi.go_to.model.place.Place;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class PlaceItemWriterTest {

    private JdbcTemplate jdbcTemplate;
    private PlaceItemWriter writer;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        TourApiBfDetailsNormalizer bfDetailsNormalizer = mock(TourApiBfDetailsNormalizer.class);
        writer = new PlaceItemWriter(jdbcTemplate, bfDetailsNormalizer);
    }

    @Test
    @DisplayName("카테고리 upsert는 current category_code를 사용하고 tombstone에서는 기존 코드를 보존한다")
    void upsertUsesCurrentCategoryCodeAndPreservesItForTombstone() {
        String sql = (String) ReflectionTestUtils.getField(PlaceItemWriter.class, "UPSERT_SQL");

        assertThat(sql).contains("category_code");
        assertThat(sql).contains("CASE WHEN EXCLUDED.is_deleted THEN places.category_code");
        assertThat(sql).doesNotContain(" category,");
        assertThat(sql).doesNotContain("category =");
    }

    @Test
    @DisplayName("빈 Chunk에 write하면 DB 작업 없이 정상 종료된다")
    void testWrite_WithEmptyChunk_ShouldReturnImmediately() throws Exception {
        Chunk<PlaceProcessingResult> chunk = new Chunk<>(Collections.emptyList());

        writer.write(chunk);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Chunk item source가 모두 같으면 write로 batchUpdate가 정상 수행된다")
    void testWrite_WithSameSource_ShouldSucceed() throws Exception {
        Place place1 = Place.builder()
                .externalId("1")
                .source("TOUR_API")
                .name("Place 1")
                .build();
        Place place2 = Place.builder()
                .externalId("2")
                .source("TOUR_API")
                .name("Place 2")
                .build();
        PlaceProcessingResult res1 = new PlaceProcessingResult(place1, null, null);
        PlaceProcessingResult res2 = new PlaceProcessingResult(place2, null, null);
        Chunk<PlaceProcessingResult> chunk = new Chunk<>(List.of(res1, res2));

        writer.write(chunk);

        verify(jdbcTemplate, times(1)).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    @DisplayName("Chunk item source가 서로 다르면 write 시 MixedSourceChunkException이 발생한다")
    void testWrite_WithDifferentSources_ShouldThrowException() {
        Place place1 = Place.builder()
                .externalId("1")
                .source("TOUR_API")
                .name("Place 1")
                .build();
        Place place2 =
                Place.builder().externalId("2").source("SSIS").name("Place 2").build();
        PlaceProcessingResult res1 = new PlaceProcessingResult(place1, null, null);
        PlaceProcessingResult res2 = new PlaceProcessingResult(place2, null, null);
        Chunk<PlaceProcessingResult> chunk = new Chunk<>(List.of(res1, res2));

        assertThatThrownBy(() -> writer.write(chunk)).isInstanceOf(MixedSourceChunkException.class);

        verifyNoInteractions(jdbcTemplate);
    }
}
