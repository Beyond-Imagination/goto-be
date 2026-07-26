package kr.bi.go_to.batch.category.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.bi.go_to.batch.category.client.TourApiCategoryClient;
import kr.bi.go_to.batch.category.dto.TourApiCategory;
import kr.bi.go_to.batch.category.dto.TourApiCategoryPage;
import kr.bi.go_to.batch.category.exception.TourApiCategorySnapshotException;
import kr.bi.go_to.batch.category.sync.TourApiCategorySynchronizer;
import kr.bi.go_to.batch.category.validation.TourApiCategorySnapshotValidator;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TourApiCategoryRepositoryIntegrationTest {

    @Autowired
    private TourApiCategoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM places");
        jdbcTemplate.update("DELETE FROM tour_api_categories");
    }

    @Test
    @DisplayName("분류체계를 원자적으로 publish하고 정확한 활성 1→2→3 계층을 반환한다")
    void publishesAtomicallyAndResolvesExactlyThreeOrderedAncestors() {
        repository.publish(categories("L1", "M1", "S1"), UUID.randomUUID());

        assertThat(repository.isActiveLeaf("S1")).isTrue();
        assertThat(repository.findActiveLeafAncestry("S1"))
                .extracting(node -> node.code() + ":" + node.depth())
                .containsExactly("L1:1", "M1:2", "S1:3");
    }

    @Test
    @DisplayName("새 스냅샷은 미확인 분류를 비활성화하고 손상 계층 조회는 즉시 실패한다")
    void newSnapshotDeactivatesUnseenRowsAndBrokenAncestryFailsClosed() {
        repository.publish(categories("L1", "M1", "S1"), UUID.randomUUID());
        repository.publish(categories("L2", "M2", "S2"), UUID.randomUUID());

        assertThat(repository.isActiveLeaf("S1")).isFalse();
        assertThat(repository.isActiveLeaf("S2")).isTrue();
        assertThatThrownBy(() -> repository.findActiveLeafAncestry("S1"))
                .isInstanceOf(TourApiCategorySnapshotException.class);
    }

    @Test
    @DisplayName("소분류가 활성이어도 부모가 비활성이면 정확한 활성 계층 검증이 즉시 실패한다")
    void activeLeafWithInactiveParentFailsHierarchyValidation() {
        repository.publish(categories("L1", "M1", "S1"), UUID.randomUUID());
        jdbcTemplate.update("UPDATE tour_api_categories SET active = FALSE WHERE code = 'M1'");

        assertThat(repository.isActiveLeaf("S1")).isTrue();
        assertThatThrownBy(() -> repository.findActiveLeafAncestry("S1"))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    @DisplayName("부모 누락·depth 오류·cycle이 있는 활성 소분류 계층은 모두 비스킵 손상으로 실패한다")
    void missingParentWrongDepthAndCycleAllFailHierarchyValidation() {
        repository.publish(categories("L1", "M1", "S1"), UUID.randomUUID());
        corruptHierarchy("UPDATE tour_api_categories SET parent_code = 'MISSING' WHERE code = 'S1'");
        assertBrokenAncestry("S1");

        repository.publish(categories("L1", "M1", "S1"), UUID.randomUUID());
        corruptHierarchy("UPDATE tour_api_categories SET depth = 1 WHERE code = 'M1'");
        assertBrokenAncestry("S1");

        repository.publish(categories("L1", "M1", "S1"), UUID.randomUUID());
        corruptHierarchy("UPDATE tour_api_categories SET parent_code = 'S1' WHERE code = 'M1'");
        assertBrokenAncestry("S1");
    }

    @Test
    @DisplayName("분류체계 publish 실패 시 이름·토큰·활성·타임스탬프를 포함한 운영 상태가 완전히 보존된다")
    void failedPublicationRollsBackEveryLiveTaxonomyMutation() {
        repository.publish(categories("L1", "M1", "S1"), UUID.randomUUID());
        List<Map<String, Object>> before = taxonomyState();

        assertThatThrownBy(() -> repository.publish(
                        List.of(
                                new TourApiCategory("L2", null, (short) 1, "large 2"),
                                new TourApiCategory("S2", "MISSING", (short) 3, "small 2")),
                        UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);

        assertThat(taxonomyState()).isEqualTo(before);
    }

    @Test
    @DisplayName("빈 taxonomy 응답이 실패하면 기존 이름·토큰·활성·타임스탬프가 완전히 보존된다")
    void emptySnapshotFailureLeavesLiveTaxonomyCompletelyUnchanged() {
        repository.publish(categories("L1", "M1", "S1"), UUID.randomUUID());
        List<Map<String, Object>> before = taxonomyState();
        TourApiCategoryClient client = mock(TourApiCategoryClient.class);
        when(client.fetchPage(1, 100)).thenReturn(new TourApiCategoryPage(1, 100, 0, List.of()));
        TourApiCategorySynchronizer synchronizer =
                new TourApiCategorySynchronizer(client, new TourApiCategorySnapshotValidator(), repository, 100);

        assertThatThrownBy(synchronizer::synchronize)
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("must be positive");

        assertThat(taxonomyState()).isEqualTo(before);
    }

    @Test
    @DisplayName("출처별 coverage가 모든 안전성 지표를 집계하고 위험 분류를 차단한다")
    void coverageReportsEveryMetricBySourceAndBlocksEveryUnsafeCategory() {
        repository.publish(
                List.of(
                        new TourApiCategory("L1", null, (short) 1, "large 1"),
                        new TourApiCategory("M1", "L1", (short) 2, "middle 1"),
                        new TourApiCategory("S1", "M1", (short) 3, "small 1"),
                        new TourApiCategory("L2", null, (short) 1, "large 2"),
                        new TourApiCategory("M2", "L2", (short) 2, "middle 2"),
                        new TourApiCategory("S2", "M2", (short) 3, "small 2"),
                        new TourApiCategory("L3", null, (short) 1, "large 3"),
                        new TourApiCategory("M3", "L3", (short) 2, "middle 3"),
                        new TourApiCategory("L4", null, (short) 1, "large 4"),
                        new TourApiCategory("M4", "L4", (short) 2, "middle 4"),
                        new TourApiCategory("S4", "M4", (short) 3, "small 4")),
                UUID.randomUUID());
        jdbcTemplate.update("UPDATE tour_api_categories SET active = FALSE WHERE code IN ('M2', 'S4')");

        jdbcTemplate.execute("SET session_replication_role = replica");
        try {
            insertPlace("resolved", "TOUR_API", "S1");
            insertPlace("null", "TOUR_API", null);
            insertPlace("blank", "TOUR_API", " ");
            insertPlace("orphan", "TOUR_API", "UNKNOWN");
            insertPlace("inactive", "TOUR_API", "S4");
            insertPlace("non-leaf", "TOUR_API", "M3");
            insertPlace("broken", "TOUR_API", "S2");
            insertPlace("non-tour", "USER", "S1");
        } finally {
            jdbcTemplate.execute("SET session_replication_role = origin");
        }

        var coverage = repository.coverageBySource();

        assertThat(coverage)
                .filteredOn(row -> row.source().equals("TOUR_API"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.total()).isEqualTo(7);
                    assertThat(row.resolved()).isEqualTo(1);
                    assertThat(row.nullCount()).isEqualTo(1);
                    assertThat(row.blank()).isEqualTo(1);
                    assertThat(row.orphan()).isEqualTo(1);
                    assertThat(row.inactive()).isEqualTo(1);
                    assertThat(row.nonLeaf()).isEqualTo(1);
                    assertThat(row.brokenAncestry()).isEqualTo(1);
                    assertThat(row.nonTourNonNull()).isZero();
                    assertThat(row.blocksIngestion()).isTrue();
                });
        assertThat(coverage)
                .filteredOn(row -> row.source().equals("USER"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.total()).isEqualTo(1);
                    assertThat(row.resolved()).isEqualTo(1);
                    assertThat(row.nonTourNonNull()).isEqualTo(1);
                    assertThat(row.blocksIngestion()).isTrue();
                });
    }

    private void insertPlace(String externalId, String source, String categoryCode) {
        jdbcTemplate.update(
                "INSERT INTO places (external_id, source, category_code, name) VALUES (?, ?, ?, ?)",
                externalId,
                source,
                categoryCode,
                externalId);
    }

    private void corruptHierarchy(String sql) {
        jdbcTemplate.execute("SET session_replication_role = replica");
        try {
            jdbcTemplate.update(sql);
        } finally {
            jdbcTemplate.execute("SET session_replication_role = origin");
        }
    }

    private void assertBrokenAncestry(String leafCode) {
        assertThatThrownBy(() -> repository.findActiveLeafAncestry(leafCode))
                .isInstanceOf(TourApiCategorySnapshotException.class)
                .hasMessageContaining("exactly one");
    }

    private List<Map<String, Object>> taxonomyState() {
        return jdbcTemplate.queryForList(
                """
                SELECT code, parent_code, depth, name, active, last_seen_sync_token, created_at, updated_at
                FROM tour_api_categories
                ORDER BY code
                """);
    }

    private List<TourApiCategory> categories(String large, String middle, String small) {
        return List.of(
                new TourApiCategory(large, null, (short) 1, "large"),
                new TourApiCategory(middle, large, (short) 2, "middle"),
                new TourApiCategory(small, middle, (short) 3, "small"));
    }
}
