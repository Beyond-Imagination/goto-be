package kr.bi.go_to.member;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지용 QueryDSL 쿼리(합계 · fetch join 목록 · 상태 필터 카운트)가
 * 실제로 의도한 대로 필터링·정렬·집계하는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class MyPageQueryDslRepositoryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ObstacleReportRepository obstacleReportRepository;

    @Autowired
    ObstacleReportConfirmationRepository obstacleReportConfirmationRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    EntityManager entityManager;

    /** 테스트 간 닉네임 유니크 제약 충돌을 막기 위한 일련번호. */
    private static int nicknameSequence = 0;

    Member me;
    Member other;

    @BeforeEach
    void setUp() {
        obstacleReportConfirmationRepository.deleteAll();
        obstacleReportRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        nicknameSequence += 1;
        me = memberRepository.save(new Member(Role.USER, "쿼리나" + nicknameSequence));
        other = memberRepository.save(new Member(Role.USER, "쿼리남" + nicknameSequence));
    }

    private static Point point(double lng, double lat) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));
    }

    private ObstacleReport saveReport(Member reporter, int confirmedCount) {
        return obstacleReportRepository.save(ObstacleReport.builder()
                .reporter(reporter)
                .locationPoint(point(126.978, 37.5665))
                .issueType(ObstacleIssueType.SIDEWALK_DAMAGE)
                .severity(ObstacleSeverity.CAUTION)
                .affectedMobilityTypes(Set.of(MobilityType.WHEELCHAIR))
                .photoUrls(List.of())
                .confirmedCount(confirmedCount)
                .lastConfirmedAt(Instant.now())
                .build());
    }

    private ObstacleReport saveResolvedReport(Member reporter) {
        ObstacleReport report = saveReport(reporter, 1);
        report.resolve();
        return obstacleReportRepository.saveAndFlush(report);
    }

    private ObstacleReportConfirmation saveConfirmation(Member member, ObstacleReport report) {
        return obstacleReportConfirmationRepository.saveAndFlush(ObstacleReportConfirmation.builder()
                .obstacleReport(report)
                .member(member)
                .build());
    }

    // ── sumConfirmedCountByReporter ─────────────────────────────────

    @Test
    @DisplayName("확인 수 합계는 내 제보만 더한다")
    void sumsOnlyMyReports() {
        saveReport(me, 5);
        saveReport(me, 3);
        saveReport(other, 100);

        assertThat(obstacleReportRepository.sumConfirmedCountByReporter(me.getId()))
                .isEqualTo(8L);
        assertThat(obstacleReportRepository.sumConfirmedCountByReporter(other.getId()))
                .isEqualTo(100L);
    }

    @Test
    @DisplayName("제보가 없으면 확인 수 합계는 null이 아니라 0이다")
    void sumIsZeroWhenNoReports() {
        assertThat(obstacleReportRepository.sumConfirmedCountByReporter(me.getId()))
                .isZero();
    }

    @Test
    @DisplayName("확인 수가 모두 0이면 합계도 0이다")
    void sumIsZeroWhenAllCountsAreZero() {
        saveReport(me, 0);
        saveReport(me, 0);

        assertThat(obstacleReportRepository.sumConfirmedCountByReporter(me.getId()))
                .isZero();
    }

    // ── findMineWithReport ──────────────────────────────────────────

    @Test
    @DisplayName("내가 확인한 목록은 내 확인 기록만 최신순으로 돌려준다")
    void findsOnlyMyConfirmationsInLatestOrder() {
        ObstacleReport first = saveReport(other, 1);
        ObstacleReport second = saveReport(other, 1);
        ObstacleReport notMine = saveReport(other, 1);

        ObstacleReportConfirmation older = saveConfirmation(me, first);
        ObstacleReportConfirmation newer = saveConfirmation(me, second);
        saveConfirmation(other, notMine);

        List<ObstacleReportConfirmation> mine = obstacleReportConfirmationRepository.findMineWithReport(me.getId());

        assertThat(mine).extracting(ObstacleReportConfirmation::getId).containsExactly(newer.getId(), older.getId());
        assertThat(mine)
                .extracting(confirmation -> confirmation.getObstacleReport().getId())
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("확인 기록이 없으면 빈 목록을 돌려준다")
    void findsEmptyWhenNoConfirmations() {
        saveReport(other, 1);

        assertThat(obstacleReportConfirmationRepository.findMineWithReport(me.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("fetch join으로 제보를 함께 로딩하므로 트랜잭션 밖에서도 제보 필드를 읽을 수 있다")
    void fetchJoinLoadsReportEagerly() {
        ObstacleReport target = saveReport(other, 4);
        saveConfirmation(me, target);
        // 영속성 컨텍스트를 비워 프록시가 아닌 fetch join 결과임을 보장한다.
        entityManager.clear();

        List<ObstacleReportConfirmation> mine = obstacleReportConfirmationRepository.findMineWithReport(me.getId());

        assertThat(mine).hasSize(1);
        ObstacleReport loaded = mine.get(0).getObstacleReport();
        assertThat(org.hibernate.Hibernate.isInitialized(loaded)).isTrue();
        assertThat(loaded.getConfirmedCount()).isEqualTo(4);
        assertThat(loaded.getIssueType()).isEqualTo(ObstacleIssueType.SIDEWALK_DAMAGE);
    }

    // ── countResolvedByMember ───────────────────────────────────────

    @Test
    @DisplayName("해결 확인 수는 해결된 제보에 대한 내 확인만 센다")
    void countsOnlyMyConfirmationsOnResolvedReports() {
        ObstacleReport resolved = saveResolvedReport(other);
        ObstacleReport active = saveReport(other, 1);
        ObstacleReport resolvedButConfirmedByOther = saveResolvedReport(other);

        saveConfirmation(me, resolved);
        saveConfirmation(me, active);
        saveConfirmation(other, resolvedButConfirmedByOther);

        assertThat(obstacleReportConfirmationRepository.countResolvedByMember(me.getId()))
                .isEqualTo(1L);
        assertThat(obstacleReportConfirmationRepository.countResolvedByMember(other.getId()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("해결된 제보를 확인한 적이 없으면 해결 확인 수는 0이다")
    void countIsZeroWhenNothingResolved() {
        saveConfirmation(me, saveReport(other, 1));

        assertThat(obstacleReportConfirmationRepository.countResolvedByMember(me.getId()))
                .isZero();
    }

    @Test
    @DisplayName("제보가 나중에 해결되면 해결 확인 수에 반영된다")
    void countReflectsLaterResolution() {
        ObstacleReport report = saveReport(other, 1);
        saveConfirmation(me, report);
        assertThat(obstacleReportConfirmationRepository.countResolvedByMember(me.getId()))
                .isZero();

        ObstacleReport managed =
                obstacleReportRepository.findById(report.getId()).orElseThrow();
        managed.resolve();
        obstacleReportRepository.saveAndFlush(managed);

        assertThat(obstacleReportConfirmationRepository.countResolvedByMember(me.getId()))
                .isEqualTo(1L);
    }
}
