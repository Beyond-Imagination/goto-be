package kr.bi.go_to.repository;

import kr.bi.go_to.model.place.PlaceBfInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceBfInfoRepository extends JpaRepository<PlaceBfInfo, Long> {}
