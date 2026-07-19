package kr.bi.go_to.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.model.map.FacilityNode;
import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
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
class FacilityNodeRepositoryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    FacilityNodeRepository facilityNodeRepository;

    @Autowired
    FloorMapRepository floorMapRepository;

    @Autowired
    PlaceRepository placeRepository;

    FloorMap floorMapOnFloor1;
    FloorMap floorMapOnFloor2;

    @BeforeEach
    void setUp() {
        facilityNodeRepository.deleteAll();
        floorMapRepository.deleteAll();
        placeRepository.deleteAll();

        Place place = placeRepository.save(Place.builder()
                .externalId("test-place-1")
                .source("TEST")
                .name("테스트 장소")
                .build());
        floorMapOnFloor1 = floorMapRepository.save(
                FloorMap.builder().place(place).floorLevel(1).build());
        floorMapOnFloor2 = floorMapRepository.save(
                FloorMap.builder().place(place).floorLevel(2).build());
    }

    @Test
    void 같은_도면에_속한_노드만_조회한다() {
        facilityNodeRepository.save(FacilityNode.builder()
                .floorMap(floorMapOnFloor1)
                .nodeType("ELEVATOR")
                .geojsonPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(126.977, 37.579)))
                .build());
        facilityNodeRepository.save(FacilityNode.builder()
                .floorMap(floorMapOnFloor2)
                .nodeType("TOILET")
                .geojsonPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(126.978, 37.580)))
                .build());

        List<FacilityNode> found = facilityNodeRepository.findByFloorMap_Id(floorMapOnFloor1.getId());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getNodeType()).isEqualTo("ELEVATOR");
    }

    @Test
    void 도면에_노드가_없으면_빈_목록을_반환한다() {
        List<FacilityNode> found = facilityNodeRepository.findByFloorMap_Id(floorMapOnFloor1.getId());

        assertThat(found).isEmpty();
    }
}
