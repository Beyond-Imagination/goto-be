package kr.bi.go_to.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.bi.go_to.model.push.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByMember_Id(Long memberId);

    List<DeviceToken> findByMember_IdIn(Collection<Long> memberIds);

    void deleteByToken(String token);

    @Modifying
    @Query("delete from DeviceToken d where d.token in :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);

    /**
     * 좌표에서 반경 안에 있고, 위치를 최근에 보고한 기기들.
     *
     * <p>오래된 위치를 그대로 쓰면 "지금 그 근처에 있는 사람"이 아니라 "한때 거기 있던 사람"에게
     * 도움 요청이 가므로 보고 시각으로도 자른다.
     */
    @Query(
            value =
                    """
                    SELECT d.* FROM device_tokens d
                    WHERE d.last_location IS NOT NULL
                      AND d.last_location_at >= :locationFreshAfter
                      AND d.member_id <> :excludedMemberId
                      AND ST_DWithin(
                          d.last_location,
                          ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                          :radiusMeters
                      )
                    """,
            nativeQuery = true)
    List<DeviceToken> findWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters,
            @Param("locationFreshAfter") Instant locationFreshAfter,
            @Param("excludedMemberId") Long excludedMemberId);
}
