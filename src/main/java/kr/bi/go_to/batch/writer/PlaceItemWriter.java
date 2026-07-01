package kr.bi.go_to.batch.writer;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            INSERT INTO places (external_id, source, category, name, sanitized_address, location_point, thumbnail_url, overview, homepage, tel, content_type_id, is_deleted, detail_common_synced, detail_with_tour_synced, detail_intro_synced, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (external_id, source)
            DO UPDATE SET
                category = COALESCE(EXCLUDED.category, places.category),
                name = COALESCE(EXCLUDED.name, places.name),
                sanitized_address = COALESCE(EXCLUDED.sanitized_address, places.sanitized_address),
                location_point = COALESCE(EXCLUDED.location_point, places.location_point),
                thumbnail_url = COALESCE(EXCLUDED.thumbnail_url, places.thumbnail_url),
                overview = COALESCE(EXCLUDED.overview, places.overview),
                homepage = COALESCE(EXCLUDED.homepage, places.homepage),
                tel = COALESCE(EXCLUDED.tel, places.tel),
                content_type_id = COALESCE(EXCLUDED.content_type_id, places.content_type_id),
                is_deleted = EXCLUDED.is_deleted,
                detail_common_synced = EXCLUDED.detail_common_synced,
                detail_with_tour_synced = EXCLUDED.detail_with_tour_synced,
                detail_intro_synced = EXCLUDED.detail_intro_synced,
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

        // 하나의 job에서 etl step 각각에 대해서는 항상 chunk 별로 datasource가 동일해야합니다.
        // 즉 하나의 작업에서는 하나의 datasource에서 온다는 뜻
        String source = items.get(0).getSource();
        boolean allSameSource = items.stream().allMatch(place -> source.equals(place.getSource()));
        if (!allSameSource) {
            throw new MixedSourceChunkException();
        }

        jdbcTemplate.batchUpdate(UPSERT_SQL, items, items.size(), (PreparedStatement ps, Place place) -> {
            ps.setString(1, place.getExternalId());
            ps.setString(2, place.getSource());
            ps.setString(3, place.getCategory());
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
        });

        log.info("Saved/Updated {} places to database using Native Upsert.", chunk.size());

        List<String> externalIds = items.stream().map(Place::getExternalId).collect(Collectors.toList());

        if (externalIds.isEmpty()) {
            return;
        }

        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("externalIds", externalIds);
        parameters.addValue("source", source);

        String selectSql =
                "SELECT id, external_id FROM places WHERE external_id IN (:externalIds) AND source = :source";

        Map<String, Long> externalIdToIdMap = namedJdbcTemplate.query(selectSql, parameters, rs -> {
            Map<String, Long> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("external_id"), rs.getLong("id"));
            }
            return map;
        });

        if (externalIdToIdMap == null) {
            return;
        }

        List<PlaceProcessingResult> resultsWithBfInfo = results.stream()
                .filter(r -> !r.place().isDeleted())
                .filter(r -> r.detailWithTourSynced() && r.detailIntroSynced())
                .filter(r -> r.bfDetails() != null
                        && r.introDetails() != null
                        && externalIdToIdMap.containsKey(r.place().getExternalId()))
                .collect(Collectors.toList());

        if (resultsWithBfInfo.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                UPSERT_BF_INFO_SQL,
                resultsWithBfInfo,
                resultsWithBfInfo.size(),
                (PreparedStatement ps, PlaceProcessingResult result) -> {
                    Long placeId = externalIdToIdMap.get(result.place().getExternalId());
                    String normalizedBfDetails = bfDetailsNormalizer.normalize(
                            result.place().getExternalId(), result.bfDetails(), result.introDetails());
                    ps.setLong(1, placeId);
                    ps.setString(2, normalizedBfDetails);
                });
        log.info("Saved/Updated {} place_bf_info records to database.", resultsWithBfInfo.size());
    }
}
