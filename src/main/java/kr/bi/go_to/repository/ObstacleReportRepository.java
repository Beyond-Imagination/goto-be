package kr.bi.go_to.repository;

import java.util.List;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ObstacleReportRepository extends JpaRepository<ObstacleReport, Long> {

    @Query(
            value =
                    """
                    SELECT r.* FROM obstacle_reports r
                    WHERE ST_Within(r.location_point, ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326))
                    AND (:mobilityType IS NULL OR EXISTS (
                        SELECT 1 FROM obstacle_report_affected_mobility_types m
                        WHERE m.obstacle_report_id = r.id AND m.mobility_type = :mobilityType
                    ))
                    """,
            nativeQuery = true)
    List<ObstacleReport> findWithinBbox(
            @Param("minLng") double minLng,
            @Param("minLat") double minLat,
            @Param("maxLng") double maxLng,
            @Param("maxLat") double maxLat,
            @Param("mobilityType") String mobilityType);

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
                    """,
            nativeQuery = true)
    List<ObstacleReport> findActiveWithinRadius(
            @Param("lng") double lng, @Param("lat") double lat, @Param("radiusMeters") double radiusMeters);
}
