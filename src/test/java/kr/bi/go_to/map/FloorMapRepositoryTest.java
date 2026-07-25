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
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("존재하는 장소와 층으로 도면을 조회한다")
    void findsFloorMapByExistingPlaceAndFloorLevel() {
        FloorMap floorMap = floorMapRepository.save(
                FloorMap.builder().place(place).floorLevel(1).build());

        Optional<FloorMap> found = floorMapRepository.findByPlace_IdAndFloorLevel(place.getId(), 1);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(floorMap.getId());
    }

    @Test
    @DisplayName("존재하지 않는 층이면 빈 값을 반환한다")
    void returnsEmptyWhenFloorLevelDoesNotExist() {
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(1).build());

        Optional<FloorMap> found = floorMapRepository.findByPlace_IdAndFloorLevel(place.getId(), 2);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("장소의 층 목록을 오름차순으로 반환한다")
    void returnsFloorLevelsInAscendingOrder() {
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(2).build());
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(-1).build());
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(1).build());

        List<Integer> floorLevels = floorMapRepository.findFloorLevelByPlace_IdOrderByFloorLevelAsc(place.getId());

        assertThat(floorLevels).containsExactly(-1, 1, 2);
    }

    @Test
    @DisplayName("등록된 도면이 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenNoFloorMapsExist() {
        List<Integer> floorLevels = floorMapRepository.findFloorLevelByPlace_IdOrderByFloorLevelAsc(place.getId());

        assertThat(floorLevels).isEmpty();
    }
}
