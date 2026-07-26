package kr.bi.go_to.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.map.FacilityNode;
import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.ReportRepository;
import kr.bi.go_to.service.report.DbReportService;
import kr.bi.go_to.service.report.model.ReportData;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    ClockConfig.class,
    JpaAuditConfig.class,
    DbReportService.class,
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DbReportServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    DbReportService dbReportService;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    FacilityNodeRepository facilityNodeRepository;

    @Autowired
    FloorMapRepository floorMapRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    private Member reporter;
    private FacilityNode node;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        facilityNodeRepository.deleteAll();
        floorMapRepository.deleteAll();
        placeRepository.deleteAll();
        memberRepository.deleteAll();

        reporter = memberRepository.save(new Member(Role.USER, "reporter"));
        Place place = placeRepository.save(Place.builder()
                .externalId("report-place")
                .source("TEST")
                .name("Report Place")
                .build());
        FloorMap floorMap = floorMapRepository.save(
                FloorMap.builder().place(place).floorLevel(2).build());
        node = facilityNodeRepository.save(FacilityNode.builder()
                .floorMap(floorMap)
                .nodeType("ELEVATOR")
                .name("Main Elevator")
                .geojsonPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(126.980470, 37.523850)))
                .isCheckpoint(true)
                .snapRadius(5)
                .build());
    }

    @Test
    void createsReportInDatabaseAndReturnsNodeData() {
        ReportData report = dbReportService.create(reporter.getId(), node.getId(), " BROKEN ", " stopped ");

        assertThat(report.id()).isNotNull();
        assertThat(report.reporterId()).isEqualTo(reporter.getId());
        assertThat(report.issueType()).isEqualTo("BROKEN");
        assertThat(report.description()).isEqualTo("stopped");
        assertThat(report.node().id()).isEqualTo(node.getId());
        assertThat(report.node().latitude()).isEqualTo(37.523850);
        assertThat(report.node().longitude()).isEqualTo(126.980470);
        assertThat(reportRepository.findById(report.id())).isPresent();
    }

    @Test
    void throwsWhenReporterDoesNotExist() {
        assertThatThrownBy(() -> dbReportService.create(999L, node.getId(), "BROKEN", null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void throwsWhenFacilityNodeDoesNotExist() {
        assertThatThrownBy(() -> dbReportService.create(reporter.getId(), 999L, "BROKEN", null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FACILITY_NODE_NOT_FOUND));
    }
}
