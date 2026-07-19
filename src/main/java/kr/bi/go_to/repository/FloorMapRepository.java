package kr.bi.go_to.repository;

import java.util.List;
import java.util.Optional;
import kr.bi.go_to.model.map.FloorMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FloorMapRepository extends JpaRepository<FloorMap, Long> {

    Optional<FloorMap> findByPlace_IdAndFloorLevel(Long placeId, Integer floorLevel);

    @Query("select fm.floorLevel from FloorMap fm where fm.place.id = :placeId order by fm.floorLevel asc")
    List<Integer> findFloorLevelByPlace_IdOrderByFloorLevelAsc(@Param("placeId") Long placeId);
}
