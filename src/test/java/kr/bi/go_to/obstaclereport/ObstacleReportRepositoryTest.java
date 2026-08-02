package kr.bi.go_to.obstaclereport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
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
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    ClockConfig.class,
    JpaAuditConfig.class,
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ObstacleReportRepositoryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    ObstacleReportRepository obstacleReportRepository;

    @Autowired
    ObstacleReportConfirmationRepository obstacleReportConfirmationRepository;

    @Autowired
    MemberRepository memberRepository;

    Member reporter;

    @BeforeEach
    void setUp() {
        obstacleReportConfirmationRepository.deleteAll();
        obstacleReportRepository.deleteAll();
        memberRepository.deleteAll();

        reporter = memberRepository.save(new Member(Role.USER, "reporter"));
    }

    @Test
    @DisplayName("bbox 내부의 제보만 반환한다")
    void findsReportsWithinBbox() {
        ObstacleReport inside = obstacleReportRepository.save(newReport(37.55, 127.0, Set.of(MobilityType.WHEELCHAIR)));
        obstacleReportRepository.save(newReport(38.0, 128.0, Set.of(MobilityType.WHEELCHAIR)));

        List<ObstacleReport> found = obstacleReportRepository.findWithinBbox(126.9, 37.5, 127.1, 37.6, null);

        assertThat(found).extracting(ObstacleReport::getId).containsExactly(inside.getId());
    }

    @Test
    @DisplayName("mobilityType으로 필터링하면 해당 이동조건을 포함하는 제보만 반환한다")
    void filtersByMobilityType() {
        ObstacleReport wheelchairReport =
                obstacleReportRepository.save(newReport(37.55, 127.0, Set.of(MobilityType.WHEELCHAIR)));
        obstacleReportRepository.save(newReport(37.55, 127.0, Set.of(MobilityType.SLOW_WALKER)));

        List<ObstacleReport> found =
                obstacleReportRepository.findWithinBbox(126.9, 37.5, 127.1, 37.6, MobilityType.WHEELCHAIR.name());

        assertThat(found).extracting(ObstacleReport::getId).containsExactly(wheelchairReport.getId());
    }

    @Test
    @DisplayName("같은 회원-제보 조합을 중복 확인하면 유니크 제약 위반이 발생한다")
    void throwsWhenConfirmingDuplicateMemberAndReport() {
        ObstacleReport report = obstacleReportRepository.save(newReport(37.55, 127.0, Set.of(MobilityType.WHEELCHAIR)));
        obstacleReportConfirmationRepository.save(ObstacleReportConfirmation.builder()
                .obstacleReport(report)
                .member(reporter)
                .build());

        assertThatThrownBy(() -> obstacleReportConfirmationRepository.saveAndFlush(ObstacleReportConfirmation.builder()
                        .obstacleReport(report)
                        .member(reporter)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("확인 기록이 없으면 존재 여부가 false다")
    void existsByReportAndMemberWhenNotConfirmed() {
        ObstacleReport report = obstacleReportRepository.save(newReport(37.55, 127.0, Set.of(MobilityType.WHEELCHAIR)));

        boolean exists = obstacleReportConfirmationRepository.existsByObstacleReport_IdAndMember_Id(
                report.getId(), reporter.getId());

        assertThat(exists).isFalse();
    }

    private ObstacleReport newReport(double lat, double lng, Set<MobilityType> mobilityTypes) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));
        return ObstacleReport.builder()
                .reporter(reporter)
                .locationPoint(point)
                .issueType(ObstacleIssueType.SIDEWALK_DAMAGE)
                .severity(ObstacleSeverity.CAUTION)
                .affectedMobilityTypes(mobilityTypes)
                .build();
    }
}
