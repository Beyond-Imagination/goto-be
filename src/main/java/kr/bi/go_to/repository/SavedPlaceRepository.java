package kr.bi.go_to.repository;

import java.util.List;
import java.util.Optional;
import kr.bi.go_to.model.savedplace.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {

    boolean existsByMember_IdAndPlace_Id(Long memberId, Long placeId);

    void deleteByMember_IdAndPlace_Id(Long memberId, Long placeId);

    List<SavedPlace> findByMember_IdOrderByCreatedAtDesc(Long memberId);

    Optional<SavedPlace> findByMember_IdAndPlace_Id(Long memberId, Long placeId);

    /** 이 장소를 저장했고 장소별 알림도 켜 둔 회원들. 장소 상태 변경 푸시의 1차 대상이다. */
    @Query("select s.member.id from SavedPlace s where s.place.id = :placeId and s.notificationEnabled = true")
    List<Long> findNotifiableMemberIdsByPlace(@Param("placeId") Long placeId);

    /**
     * 좌표 반경 안에 있는 장소를 저장해 둔 회원들 (장소별 알림이 켜진 것만).
     *
     * <p>「저장한 장소 주변 장애물」 푸시 대상이다. 장소 좌표는 geometry(4326)라서 거리 계산 전에
     * geography로 캐스팅한다. 좌표가 없는 장소는 조건에서 자연히 빠진다.
     */
    @Query(
            value =
                    """
                    SELECT DISTINCT s.member_id FROM saved_places s
                    JOIN places p ON p.id = s.place_id
                    WHERE s.notification_enabled = true
                      AND p.is_deleted = false
                      AND p.location_point IS NOT NULL
                      AND ST_DWithin(
                          p.location_point::geography,
                          ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                          :radiusMeters
                      )
                    """,
            nativeQuery = true)
    List<Long> findNotifiableMemberIdsNearby(
            @Param("lat") double lat, @Param("lng") double lng, @Param("radiusMeters") double radiusMeters);

    /** 반경 안의 저장 장소 중 가장 가까운 장소 이름. 푸시 본문에 "어디 근처인지"를 적기 위해 쓴다. */
    @Query(
            value =
                    """
                    SELECT p.name FROM saved_places s
                    JOIN places p ON p.id = s.place_id
                    WHERE s.member_id = :memberId
                      AND s.notification_enabled = true
                      AND p.is_deleted = false
                      AND p.location_point IS NOT NULL
                      AND ST_DWithin(
                          p.location_point::geography,
                          ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                          :radiusMeters
                      )
                    ORDER BY p.location_point::geography <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<String> findNearestSavedPlaceName(
            @Param("memberId") Long memberId,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters);
}
