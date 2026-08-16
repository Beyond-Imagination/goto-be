package kr.bi.go_to.repository;

import java.util.List;
import kr.bi.go_to.model.place.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceSearchRepository extends JpaRepository<Place, Long> {

    @Query(
            value =
                    """
                    SELECT
                        p.id AS id,
                        p.external_id AS externalId,
                        p.source AS source,
                        p.category_code AS category,
                        p.name AS name,
                        p.sanitized_address AS sanitizedAddress,
                        ST_Y(p.location_point) AS latitude,
                        ST_X(p.location_point) AS longitude,
                        p.thumbnail_url AS thumbnailUrl,
                        COALESCE((pbi.bf_details #>> '{mobility,elevator,is_available}')::boolean, false) AS hasElevator,
                        COALESCE((pbi.bf_details #>> '{mobility,restroom,is_available}')::boolean, false) AS hasAccessibleToilet,
                        COALESCE((pbi.bf_details #>> '{mobility,route,is_available}')::boolean, false) AS hasRamp,
                        pbi.last_synced_at AS bfLastSyncedAt,
                        ST_Distance(
                            p.location_point::geography,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                        ) AS distanceMeters
                    FROM places p
                    LEFT JOIN place_bf_info pbi ON pbi.place_id = p.id
                    WHERE p.location_point IS NOT NULL
                      AND p.is_deleted = false
                      AND (:category IS NULL OR p.category_code = :category)
                    ORDER BY distanceMeters ASC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<PlaceSearchProjection> searchNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("limit") int limit,
            @Param("category") String category);

    @Query(
            value =
                    """
                    SELECT DISTINCT p.category_code
                    FROM places p
                    WHERE p.category_code IS NOT NULL
                      AND btrim(p.category_code) <> ''
                      AND p.is_deleted = false
                    ORDER BY p.category_code ASC
                    """,
            nativeQuery = true)
    List<String> findDistinctCategories();
}
