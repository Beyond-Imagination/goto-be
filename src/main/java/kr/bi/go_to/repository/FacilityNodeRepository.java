package kr.bi.go_to.repository;

import java.util.List;
import kr.bi.go_to.model.map.FacilityNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FacilityNodeRepository extends JpaRepository<FacilityNode, Long> {

    List<FacilityNode> findByFloorMap_Id(Long floorMapId);
}
