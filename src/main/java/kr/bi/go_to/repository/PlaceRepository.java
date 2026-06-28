package kr.bi.go_to.repository;

import java.util.Optional;
import kr.bi.go_to.model.place.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByExternalIdAndSource(String externalId, String source);
}
