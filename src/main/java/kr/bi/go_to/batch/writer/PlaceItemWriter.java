package kr.bi.go_to.batch.writer;

import java.sql.PreparedStatement;
import java.util.List;
import kr.bi.go_to.model.place.Place;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceItemWriter implements ItemWriter<Place> {

    private final JdbcTemplate jdbcTemplate;

    private static final String UPSERT_SQL =
            """
            INSERT INTO places (external_id, source, category, name, sanitized_address, location_point, thumbnail_url, overview, homepage, tel, content_type_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (external_id, source)
            DO UPDATE SET
                category = EXCLUDED.category,
                name = EXCLUDED.name,
                sanitized_address = EXCLUDED.sanitized_address,
                location_point = EXCLUDED.location_point,
                thumbnail_url = EXCLUDED.thumbnail_url,
                overview = EXCLUDED.overview,
                homepage = EXCLUDED.homepage,
                tel = EXCLUDED.tel,
                content_type_id = EXCLUDED.content_type_id,
                updated_at = NOW()
            """;

    @Override
    public void write(Chunk<? extends Place> chunk) throws Exception {
        jdbcTemplate.batchUpdate(
                UPSERT_SQL, (List<Place>) chunk.getItems(), chunk.size(), (PreparedStatement ps, Place place) -> {
                    ps.setString(1, place.getExternalId());
                    ps.setString(2, place.getSource());
                    ps.setString(3, place.getCategory());
                    ps.setString(4, place.getName());
                    ps.setString(5, place.getSanitizedAddress());

                    if (place.getLocationPoint() != null) {
                        ps.setString(6, place.getLocationPoint().toText());
                    } else {
                        ps.setNull(6, java.sql.Types.VARCHAR);
                    }

                    ps.setString(7, place.getThumbnailUrl());
                    ps.setString(8, place.getOverview());
                    ps.setString(9, place.getHomepage());
                    ps.setString(10, place.getTel());
                    ps.setString(11, place.getContentTypeId());
                });

        log.info("Saved/Updated {} places to database using Native Upsert.", chunk.size());
    }
}
