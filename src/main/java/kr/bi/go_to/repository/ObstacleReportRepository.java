package kr.bi.go_to.repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ObstacleReportRepository extends JpaRepository<ObstacleReport, Long>, ObstacleReportRepositoryCustom {

    /**
     * 내가 작성한 장애물 제보를 최신순으로 조회한다. (내 정보 03 화면)
     */
    List<ObstacleReport> findByReporter_IdOrderByCreatedAtDesc(Long reporterId);

    long countByReporter_Id(Long reporterId);

    @Query(
            value =
                    """
                    SELECT r.* FROM obstacle_reports r
                    WHERE ST_Within(r.location_point, ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326))
                    AND (:hasMobilityTypes = false OR EXISTS (
                        SELECT 1 FROM obstacle_report_affected_mobility_types m
                        WHERE m.obstacle_report_id = r.id AND m.mobility_type IN (:mobilityTypes)
                    ))
                    AND (:hasAvoid = false OR r.issue_type NOT IN (:avoid))
                    """,
            nativeQuery = true)
    List<ObstacleReport> findWithinBbox(
            @Param("minLng") double minLng,
            @Param("minLat") double minLat,
            @Param("maxLng") double maxLng,
            @Param("maxLat") double maxLat,
            @Param("hasMobilityTypes") boolean hasMobilityTypes,
            @Param("mobilityTypes") Set<String> mobilityTypes,
            @Param("hasAvoid") boolean hasAvoid,
            @Param("avoid") Set<String> avoid);

    @Query(
            value =
                    """
                    SELECT r.* FROM obstacle_reports r
                    WHERE r.status = 'ACTIVE'
                    AND ST_DWithin(
                        r.location_point_geography,
                        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                        :radiusMeters
                    )
                    AND (:hasMobilityTypes = false OR EXISTS (
                        SELECT 1 FROM obstacle_report_affected_mobility_types m
                        WHERE m.obstacle_report_id = r.id AND m.mobility_type IN (:mobilityTypes)
                    ))
                    AND (:hasAvoid = false OR r.issue_type NOT IN (:avoid))
                    """,
            nativeQuery = true)
    List<ObstacleReport> findActiveWithinRadius(
            @Param("lng") double lng,
            @Param("lat") double lat,
            @Param("radiusMeters") double radiusMeters,
            @Param("hasMobilityTypes") boolean hasMobilityTypes,
            @Param("mobilityTypes") Set<String> mobilityTypes,
            @Param("hasAvoid") boolean hasAvoid,
            @Param("avoid") Set<String> avoid);

    /**
     * 확인이 오래 끊긴 ACTIVE 제보들. 제보자에게 "지금도 그대로인가요?"를 물을 대상이다.
     *
     * <p>마지막 확인(없으면 등록) 시각이 기준보다 오래됐고, 확인 요청을 아직 안 보냈거나
     * 보낸 지도 오래된 것만 고른다. 후자가 없으면 같은 제보에 매일 알림이 나간다.
     */
    @Query(
            value =
                    """
                    SELECT r.* FROM obstacle_reports r
                    WHERE r.status = 'ACTIVE'
                      AND COALESCE(r.last_confirmed_at, r.created_at) < :staleBefore
                      AND (r.confirmation_requested_at IS NULL OR r.confirmation_requested_at < :askedBefore)
                    ORDER BY COALESCE(r.last_confirmed_at, r.created_at)
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<ObstacleReport> findStaleForConfirmationRequest(
            @Param("staleBefore") Instant staleBefore,
            @Param("askedBefore") Instant askedBefore,
            @Param("limit") int limit);
}
