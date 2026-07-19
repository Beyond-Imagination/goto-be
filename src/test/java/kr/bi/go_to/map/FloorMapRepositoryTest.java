package kr.bi.go_to.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    ClockConfig.class,
    JpaAuditConfig.class,
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FloorMapRepositoryTest {

    @Autowired
    FloorMapRepository floorMapRepository;

    @Autowired
    PlaceRepository placeRepository;

    Place place;

    @BeforeEach
    void setUp() {
        floorMapRepository.deleteAll();
        placeRepository.deleteAll();
        place = placeRepository.save(Place.builder()
                .externalId("test-place-1")
                .source("TEST")
                .name("테스트 장소")
                .build());
    }

    @Test
    void 존재하는_장소와_층으로_도면을_조회한다() {
        FloorMap floorMap = floorMapRepository.save(
                FloorMap.builder().place(place).floorLevel(1).build());

        Optional<FloorMap> found = floorMapRepository.findByPlace_IdAndFloorLevel(place.getId(), 1);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(floorMap.getId());
    }

    @Test
    void 존재하지_않는_층이면_빈_값을_반환한다() {
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(1).build());

        Optional<FloorMap> found = floorMapRepository.findByPlace_IdAndFloorLevel(place.getId(), 2);

        assertThat(found).isEmpty();
    }

    @Test
    void 장소의_층_목록을_오름차순으로_반환한다() {
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(2).build());
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(-1).build());
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(1).build());

        List<Integer> floorLevels = floorMapRepository.findFloorLevelByPlace_IdOrderByFloorLevelAsc(place.getId());

        assertThat(floorLevels).containsExactly(-1, 1, 2);
    }

    @Test
    void 등록된_도면이_없으면_빈_목록을_반환한다() {
        List<Integer> floorLevels = floorMapRepository.findFloorLevelByPlace_IdOrderByFloorLevelAsc(place.getId());

        assertThat(floorLevels).isEmpty();
    }
}
