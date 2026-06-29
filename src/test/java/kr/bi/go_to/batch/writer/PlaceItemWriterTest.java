package kr.bi.go_to.batch.writer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.exception.MixedSourceChunkException;
import kr.bi.go_to.model.place.Place;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;

class PlaceItemWriterTest {

    private JdbcTemplate jdbcTemplate;
    private PlaceItemWriter writer;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        writer = new PlaceItemWriter(jdbcTemplate);
    }

    @Test
    @DisplayName("빈 Chunk에 write하면 DB 작업 없이 정상 종료된다")
    void testWrite_WithEmptyChunk_ShouldReturnImmediately() throws Exception {
        // given
        Chunk<PlaceProcessingResult> chunk = new Chunk<>(Collections.emptyList());

        // when
        writer.write(chunk);

        // then
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Chunk item source가 모두 같으면 write로 batchUpdate가 정상 수행된다")
    void testWrite_WithSameSource_ShouldSucceed() throws Exception {
        // given
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

        // when
        writer.write(chunk);

        // then
        verify(jdbcTemplate, times(1)).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    @DisplayName("Chunk item source가 서로 다르면 write 시 MixedSourceChunkException이 발생한다")
    void testWrite_WithDifferentSources_ShouldThrowException() {
        // given
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

        // when & then
        assertThatThrownBy(() -> writer.write(chunk)).isInstanceOf(MixedSourceChunkException.class);

        verifyNoInteractions(jdbcTemplate);
    }
}
