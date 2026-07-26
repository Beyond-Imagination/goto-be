-- Run before V13 to inventory legacy values. V13 blocks any non-Tour non-null category.
SELECT
    source,
    COUNT(*) AS total,
    COUNT(*) FILTER (WHERE category IS NULL) AS null_count,
    COUNT(*) FILTER (WHERE category IS NOT NULL AND BTRIM(category) = '') AS blank,
    COUNT(*) FILTER (WHERE category IS NOT NULL) AS non_null
FROM places
GROUP BY source
ORDER BY source;
