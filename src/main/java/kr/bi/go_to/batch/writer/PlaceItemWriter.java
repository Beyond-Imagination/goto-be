package kr.bi.go_to.batch.writer;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.exception.MixedSourceChunkException;
import kr.bi.go_to.batch.mapper.TourApiBfDetailsNormalizer;
import kr.bi.go_to.model.place.Place;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceItemWriter implements ItemWriter<PlaceProcessingResult> {

    private final JdbcTemplate jdbcTemplate;
    private final TourApiBfDetailsNormalizer bfDetailsNormalizer;

    private static final String UPSERT_SQL =
            """
            INSERT INTO places (
                external_id, source, category_code, name, sanitized_address,
                location_point, thumbnail_url, overview, homepage, tel,
                content_type_id, is_deleted,
                detail_common_synced, detail_with_tour_synced, detail_intro_synced,
                category_resolution_status,
                detail_common_status, detail_with_tour_status, detail_intro_status,
                detail_with_tour_payload, detail_intro_payload,
                created_at, updated_at
            )
            VALUES (
                ?, ?, ?, ?, ?,
                ST_GeomFromText(?, 4326), ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?,
                ?, ?, ?,
                ?::jsonb, ?::jsonb,
                NOW(), NOW()
            )
            ON CONFLICT (external_id, source)
            DO UPDATE SET
                -- Base fields
                category_code = CASE
                    WHEN EXCLUDED.is_deleted OR EXCLUDED.category_resolution_status = 'PENDING'
                        THEN places.category_code
                    WHEN places.category_resolution_status = 'RESOLVED'
                         AND EXCLUDED.category_resolution_status <> 'RESOLVED'
                        THEN places.category_code
                    ELSE EXCLUDED.category_code
                END,
                name = COALESCE(EXCLUDED.name, places.name),
                sanitized_address = COALESCE(EXCLUDED.sanitized_address, places.sanitized_address),
                location_point = COALESCE(EXCLUDED.location_point, places.location_point),
                thumbnail_url = COALESCE(EXCLUDED.thumbnail_url, places.thumbnail_url),
                overview = COALESCE(EXCLUDED.overview, places.overview),
                homepage = COALESCE(EXCLUDED.homepage, places.homepage),
                tel = COALESCE(EXCLUDED.tel, places.tel),
                content_type_id = COALESCE(EXCLUDED.content_type_id, places.content_type_id),
                is_deleted = EXCLUDED.is_deleted,

                -- Category may recover from NOT_FOUND, but never regresses from RESOLVED
                category_resolution_status = CASE
                    WHEN EXCLUDED.category_resolution_status = 'PENDING'
                        THEN places.category_resolution_status
                    WHEN places.category_resolution_status = 'PENDING'
                         OR (places.category_resolution_status = 'NOT_FOUND'
                             AND EXCLUDED.category_resolution_status = 'RESOLVED')
                        THEN EXCLUDED.category_resolution_status
                    ELSE places.category_resolution_status
                END,

                -- Detail states move only once from PENDING to a terminal value
                detail_common_status = CASE
                    WHEN places.detail_common_status = 'PENDING'
                         AND EXCLUDED.detail_common_status <> 'PENDING'
                        THEN EXCLUDED.detail_common_status
                    ELSE places.detail_common_status
                END,
                detail_with_tour_status = CASE
                    WHEN places.detail_with_tour_status = 'PENDING'
                         AND EXCLUDED.detail_with_tour_status <> 'PENDING'
                        THEN EXCLUDED.detail_with_tour_status
                    ELSE places.detail_with_tour_status
                END,
                detail_intro_status = CASE
                    WHEN places.detail_intro_status = 'PENDING'
                         AND EXCLUDED.detail_intro_status <> 'PENDING'
                        THEN EXCLUDED.detail_intro_status
                    ELSE places.detail_intro_status
                END,

                -- Successful raw payloads survive until both sibling endpoints complete
                detail_with_tour_payload = CASE
                    WHEN places.detail_with_tour_status = 'PENDING'
                         AND EXCLUDED.detail_with_tour_status = 'SUCCESS'
                         AND EXCLUDED.detail_with_tour_payload IS NOT NULL
                        THEN EXCLUDED.detail_with_tour_payload
                    ELSE places.detail_with_tour_payload
                END,
                detail_intro_payload = CASE
                    WHEN places.detail_intro_status = 'PENDING'
                         AND EXCLUDED.detail_intro_status = 'SUCCESS'
                         AND EXCLUDED.detail_intro_payload IS NOT NULL
                        THEN EXCLUDED.detail_intro_payload
                    ELSE places.detail_intro_payload
                END,

                -- Legacy booleans follow the same guarded transition
                detail_common_synced = CASE
                    WHEN places.detail_common_status = 'PENDING'
                         AND EXCLUDED.detail_common_status <> 'PENDING'
                        THEN EXCLUDED.detail_common_status = 'SUCCESS'
                    ELSE places.detail_common_synced
                END,
                detail_with_tour_synced = CASE
                    WHEN places.detail_with_tour_status = 'PENDING'
                         AND EXCLUDED.detail_with_tour_status <> 'PENDING'
                        THEN EXCLUDED.detail_with_tour_status = 'SUCCESS'
                    ELSE places.detail_with_tour_synced
                END,
                detail_intro_synced = CASE
                    WHEN places.detail_intro_status = 'PENDING'
                         AND EXCLUDED.detail_intro_status <> 'PENDING'
                        THEN EXCLUDED.detail_intro_status = 'SUCCESS'
                    ELSE places.detail_intro_synced
                END,
                updated_at = NOW()
            """;

    private static final String UPSERT_BF_INFO_SQL =
            """
            INSERT INTO place_bf_info (place_id, bf_details, last_synced_at, created_at, updated_at)
            VALUES (?, ?::jsonb, NOW(), NOW(), NOW())
            ON CONFLICT (place_id)
            DO UPDATE SET
                bf_details = EXCLUDED.bf_details,
                last_synced_at = EXCLUDED.last_synced_at,
                updated_at = NOW()
            """;

    @Override
    public void write(Chunk<? extends PlaceProcessingResult> chunk) throws Exception {
        List<PlaceProcessingResult> results = new ArrayList<>(chunk.getItems());
        List<Place> items = results.stream().map(PlaceProcessingResult::place).collect(Collectors.toList());

        if (items.isEmpty()) {
            return;
        }

        // 한 청크에는 동일한 데이터 출처의 장소만 포함되어야 한다.
        String source = items.get(0).getSource();
        boolean allSameSource = items.stream().allMatch(place -> source.equals(place.getSource()));
        if (!allSameSource) {
            throw new MixedSourceChunkException();
        }

        jdbcTemplate.batchUpdate(
                UPSERT_SQL, results, results.size(), (PreparedStatement ps, PlaceProcessingResult result) -> {
                    Place place = result.place();
                    ps.setString(1, place.getExternalId());
                    ps.setString(2, place.getSource());
                    ps.setString(3, place.getCategoryCode());
                    ps.setString(4, place.getName());
                    ps.setString(5, place.getSanitizedAddress());

                    if (place.getLocationPoint() != null) {
                        ps.setString(6, place.getLocationPoint().toText());
                    } else {
                        ps.setNull(6, Types.VARCHAR);
                    }

                    ps.setString(7, place.getThumbnailUrl());
                    ps.setString(8, place.getOverview());
                    ps.setString(9, place.getHomepage());
                    ps.setString(10, place.getTel());
                    ps.setString(11, place.getContentTypeId());
                    ps.setBoolean(12, place.isDeleted());
                    ps.setBoolean(13, place.isDetailCommonSynced());
                    ps.setBoolean(14, place.isDetailWithTourSynced());
                    ps.setBoolean(15, place.isDetailIntroSynced());
                    ps.setString(16, place.getCategoryResolutionStatus().name());
                    ps.setString(17, place.getDetailCommonStatus().name());
                    ps.setString(18, place.getDetailWithTourStatus().name());
                    ps.setString(19, place.getDetailIntroStatus().name());
                    ps.setString(20, result.bfDetails());
                    ps.setString(21, result.introDetails());
                });

        log.info("Saved/Updated {} places to database using Native Upsert.", chunk.size());

        List<String> payloadUpdatedExternalIds = results.stream()
                .filter(result -> !result.place().isDeleted())
                .filter(result -> result.bfDetails() != null || result.introDetails() != null)
                .map(result -> result.place().getExternalId())
                .toList();

        if (payloadUpdatedExternalIds.isEmpty()) {
            return;
        }

        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("externalIds", payloadUpdatedExternalIds);
        parameters.addValue("source", source);

        List<CompletedDetailPayload> completedPayloads = namedJdbcTemplate.query(
                """
                SELECT id, external_id,
                       detail_with_tour_payload::text,
                       detail_intro_payload::text
                FROM places
                WHERE external_id IN (:externalIds)
                  AND source = :source
                  AND is_deleted = false
                  AND detail_with_tour_status = 'SUCCESS'
                  AND detail_intro_status = 'SUCCESS'
                  AND detail_with_tour_payload IS NOT NULL
                  AND detail_intro_payload IS NOT NULL
                """,
                parameters,
                (resultSet, rowNumber) -> new CompletedDetailPayload(
                        resultSet.getLong(1), resultSet.getString(2), resultSet.getString(3), resultSet.getString(4)));

        if (completedPayloads.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                UPSERT_BF_INFO_SQL,
                completedPayloads,
                completedPayloads.size(),
                (PreparedStatement ps, CompletedDetailPayload payload) -> {
                    String normalizedBfDetails =
                            bfDetailsNormalizer.normalize(payload.externalId(), payload.withTour(), payload.intro());
                    ps.setLong(1, payload.placeId());
                    ps.setString(2, normalizedBfDetails);
                });
        log.info("Saved/Updated {} place_bf_info records to database.", completedPayloads.size());
    }

    private record CompletedDetailPayload(long placeId, String externalId, String withTour, String intro) {}
}
