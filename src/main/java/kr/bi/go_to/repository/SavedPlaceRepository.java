package kr.bi.go_to.repository;

import java.util.List;
import kr.bi.go_to.model.savedplace.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {

    boolean existsByMember_IdAndPlace_Id(Long memberId, Long placeId);

    void deleteByMember_IdAndPlace_Id(Long memberId, Long placeId);

    List<SavedPlace> findByMember_IdOrderByCreatedAtDesc(Long memberId);
}
