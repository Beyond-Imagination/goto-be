package kr.bi.go_to.model.floorMap;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorMapRepository extends JpaRepository<FloorMap, Long> {

    Optional<FloorMap> findByPlaceIdAndFloorLevel(Long placeId, Integer floorLevel);
}
