-- Run after a successful taxonomy synchronization and a clean runtime coverage audit.
SELECT
    p.source,
    COUNT(*) AS total,
    COUNT(*) FILTER (WHERE p.category_code IS NULL) AS null_count,
    COUNT(*) FILTER (
        WHERE p.category_code IS NOT NULL AND BTRIM(p.category_code) = ''
    ) AS blank,
    COUNT(*) FILTER (
        WHERE p.category_code IS NOT NULL
          AND BTRIM(p.category_code) <> ''
          AND leaf.code IS NULL
    ) AS orphan,
    COUNT(*) FILTER (WHERE leaf.code IS NOT NULL AND leaf.active = FALSE) AS inactive,
    COUNT(*) FILTER (WHERE leaf.active = TRUE AND leaf.depth <> 3) AS non_leaf,
    COUNT(*) FILTER (
        WHERE leaf.active = TRUE
          AND leaf.depth = 3
          AND NOT COALESCE((
              parent2.active = TRUE
              AND parent2.depth = 2
              AND parent1.active = TRUE
              AND parent1.depth = 1
              AND parent1.parent_code IS NULL
          ), FALSE)
    ) AS broken_ancestry,
    COUNT(*) FILTER (
        WHERE p.source <> 'TOUR_API' AND p.category_code IS NOT NULL
    ) AS non_tour_non_null
FROM places p
LEFT JOIN tour_api_categories leaf ON leaf.code = p.category_code
LEFT JOIN tour_api_categories parent2 ON parent2.code = leaf.parent_code
LEFT JOIN tour_api_categories parent1 ON parent1.code = parent2.parent_code
GROUP BY p.source
ORDER BY p.source;

-- Execute only when every blocking count above is zero.
ALTER TABLE places VALIDATE CONSTRAINT fk_places_tour_api_category;
