package kr.bi.go_to.batch.category.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.bi.go_to.batch.category.dto.TourApiCategory;
import kr.bi.go_to.batch.category.dto.TourApiCategoryNode;
import kr.bi.go_to.batch.category.exception.TourApiCategorySnapshotException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TourApiCategoryRepository {

    private static final String UPSERT_SQL =
            """
            INSERT INTO tour_api_categories (
                code, parent_code, depth, name, active, last_seen_sync_token, created_at, updated_at
            ) VALUES (?, ?, ?, ?, TRUE, ?, NOW(), NOW())
            ON CONFLICT (code) DO UPDATE SET
                parent_code = EXCLUDED.parent_code,
                depth = EXCLUDED.depth,
                name = EXCLUDED.name,
                active = TRUE,
                last_seen_sync_token = EXCLUDED.last_seen_sync_token,
                updated_at = NOW()
            """;

    private final JdbcTemplate jdbcTemplate;

    public TourApiCategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActiveLeaf(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM tour_api_categories
                    WHERE code = ? AND active = TRUE AND depth = 3
                )
                """,
                Boolean.class,
                code);
        return Boolean.TRUE.equals(exists);
    }

    @Transactional
    public void publish(List<TourApiCategory> categories, UUID syncToken) {
        jdbcTemplate.batchUpdate(UPSERT_SQL, categories, categories.size(), (statement, category) -> {
            statement.setString(1, category.code());
            if (category.parentCode() == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, category.parentCode());
            }
            statement.setShort(3, category.depth());
            statement.setString(4, category.name());
            statement.setObject(5, syncToken);
        });

        jdbcTemplate.update(
                """
                UPDATE tour_api_categories
                SET active = FALSE, updated_at = NOW()
                WHERE last_seen_sync_token <> ?
                """,
                syncToken);
    }

    public List<TourApiCategoryNode> findActiveLeafAncestry(String leafCode) {
        List<TourApiCategoryNode> nodes = jdbcTemplate.query(
                """
                WITH RECURSIVE ancestry AS (
                    SELECT code, parent_code, depth, name, ARRAY[code] AS path
                    FROM tour_api_categories
                    WHERE code = ? AND active = TRUE AND depth = 3

                    UNION ALL

                    SELECT parent.code, parent.parent_code, parent.depth, parent.name, ancestry.path || parent.code
                    FROM tour_api_categories parent
                    JOIN ancestry ON parent.code = ancestry.parent_code
                    WHERE parent.active = TRUE
                      AND NOT parent.code = ANY(ancestry.path)
                )
                SELECT code, name, depth
                FROM ancestry
                """,
                this::mapNode,
                leafCode);

        nodes.sort(Comparator.comparingInt(TourApiCategoryNode::depth));
        if (nodes.size() != 3
                || nodes.get(0).depth() != 1
                || nodes.get(1).depth() != 2
                || nodes.get(2).depth() != 3) {
            throw new TourApiCategorySnapshotException(
                    "Active leaf does not resolve to exactly one depth-1/depth-2/depth-3 ancestry: " + leafCode);
        }
        return List.copyOf(nodes);
    }

    public List<TourApiCategoryCoverage> coverageBySource() {
        return jdbcTemplate.query(
                """
                SELECT
                    p.source,
                    COUNT(*) AS total,
                    COUNT(*) FILTER (
                        WHERE c.active = TRUE
                          AND c.depth = 3
                          AND parent2.active = TRUE
                          AND parent2.depth = 2
                          AND parent1.active = TRUE
                          AND parent1.depth = 1
                          AND parent1.parent_code IS NULL
                    ) AS resolved,
                    COUNT(*) FILTER (WHERE p.category_code IS NULL) AS null_count,
                    COUNT(*) FILTER (
                        WHERE p.category_code IS NOT NULL AND BTRIM(p.category_code) = ''
                    ) AS blank,
                    COUNT(*) FILTER (
                        WHERE p.category_code IS NOT NULL
                          AND BTRIM(p.category_code) <> ''
                          AND c.code IS NULL
                    ) AS orphan,
                    COUNT(*) FILTER (
                        WHERE c.code IS NOT NULL AND c.active = FALSE
                    ) AS inactive,
                    COUNT(*) FILTER (
                        WHERE c.active = TRUE AND c.depth <> 3
                    ) AS non_leaf,
                    COUNT(*) FILTER (
                        WHERE c.active = TRUE
                          AND c.depth = 3
                          AND NOT COALESCE((
                              parent2.active = TRUE
                              AND parent2.depth = 2
                              AND parent1.active = TRUE
                              AND parent1.depth = 1
                              AND parent1.parent_code IS NULL
                          ), FALSE)
                    ) AS broken_ancestry,
                    COUNT(*) FILTER (
                        WHERE p.source <> 'TOUR_API'
                          AND p.category_code IS NOT NULL
                    ) AS non_tour_non_null
                FROM places p
                LEFT JOIN tour_api_categories c ON c.code = p.category_code
                LEFT JOIN tour_api_categories parent2 ON parent2.code = c.parent_code
                LEFT JOIN tour_api_categories parent1 ON parent1.code = parent2.parent_code
                GROUP BY p.source
                ORDER BY p.source
                """,
                (resultSet, rowNumber) -> new TourApiCategoryCoverage(
                        resultSet.getString("source"),
                        resultSet.getLong("total"),
                        resultSet.getLong("resolved"),
                        resultSet.getLong("null_count"),
                        resultSet.getLong("blank"),
                        resultSet.getLong("orphan"),
                        resultSet.getLong("inactive"),
                        resultSet.getLong("non_leaf"),
                        resultSet.getLong("broken_ancestry"),
                        resultSet.getLong("non_tour_non_null")));
    }

    private TourApiCategoryNode mapNode(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TourApiCategoryNode(
                resultSet.getString("code"), resultSet.getString("name"), resultSet.getShort("depth"));
    }

    public record TourApiCategoryCoverage(
            String source,
            long total,
            long resolved,
            long nullCount,
            long blank,
            long orphan,
            long inactive,
            long nonLeaf,
            long brokenAncestry,
            long nonTourNonNull) {

        public boolean blocksIngestion() {
            return orphan > 0 || inactive > 0 || nonLeaf > 0 || brokenAncestry > 0 || nonTourNonNull > 0;
        }
    }
}
