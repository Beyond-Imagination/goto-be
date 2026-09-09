package kr.bi.go_to.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.bi.go_to.model.placereport.PlaceStateReport;

public interface PlaceStateReportRepositoryCustom {

    /**
     * 내가 작성한 장소 상태 제보를 최신순으로 한 페이지 조회한다. (내 정보 03 화면의 「장소」 분류)
     * 목록이 장소명·주소를 함께 쓰므로 Place를 fetch join 하고, 커서가 있으면 그 항목 다음부터 읽는다.
     */
    List<PlaceStateReport> findMinePage(Long memberId, Instant afterCreatedAt, Long afterId, int limit);

    /**
     * 한 장소의 최근 제보를 최신순으로 조회한다. (장소 상세 화면)
     */
    List<PlaceStateReport> findLatestByPlace(Long placeId, int limit);

    /**
     * 상세 조회. 응답이 장소명을 쓰므로 Place를 함께 가져온다.
     */
    Optional<PlaceStateReport> findByIdWithPlace(Long reportId);
}
