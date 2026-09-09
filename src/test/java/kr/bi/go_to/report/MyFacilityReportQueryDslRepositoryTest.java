package kr.bi.go_to.report;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.map.FacilityNode;
import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.report.Report;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.ReportRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 시설 제보 조회(QueryDSL)가 작성자로 정확히 필터링하고, 최신순으로 정렬하며,
 * 노드 → 층 도면 → 장소를 실제로 fetch join 하는지 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    ClockConfig.class,
    JpaAuditConfig.class,
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class MyFacilityReportQueryDslRepositoryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

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

    /** 테스트 간 유니크 제약 충돌을 막기 위한 일련번호. */
    private static int sequence = 0;

    Member me;
    Member other;
    FacilityNode museumElevator;
    FacilityNode hallRamp;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        facilityNodeRepository.deleteAll();
        floorMapRepository.deleteAll();
        placeRepository.deleteAll();
        memberRepository.deleteAll();

        sequence += 1;
        me = memberRepository.save(new Member(Role.USER, "시설나" + sequence));
        other = memberRepository.save(new Member(Role.USER, "시설남" + sequence));

        museumElevator = saveNode("국립경주박물관", 2, "ELEVATOR", "본관 엘리베이터");
        hallRamp = saveNode("시청", -1, "RAMP", "지하 주차장 경사로");
    }

    private FacilityNode saveNode(String placeName, int floorLevel, String nodeType, String nodeName) {
        Place place = placeRepository.save(Place.builder()
                .externalId(placeName + "-" + sequence)
                .source("TEST")
                .name(placeName)
                .sanitizedAddress(placeName + " 주소")
                .build());
        FloorMap floorMap = floorMapRepository.save(
                FloorMap.builder().place(place).floorLevel(floorLevel).build());

        return facilityNodeRepository.save(FacilityNode.builder()
                .floorMap(floorMap)
                .nodeType(nodeType)
                .name(nodeName)
                .geojsonPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(129.2287, 35.8295)))
                .isCheckpoint(false)
                .build());
    }

    private Report saveReport(Member reporter, FacilityNode node, String issueType) {
        return reportRepository.saveAndFlush(Report.create(node, reporter, issueType, "메모"));
    }

    @Test
    @DisplayName("내 제보만 최신순으로 돌려주고 다른 사람 제보는 섞이지 않는다")
    void returnsOnlyMineInLatestOrder() {
        Report mineFirst = saveReport(me, museumElevator, "BROKEN");
        Report mineSecond = saveReport(me, hallRamp, "BLOCKED");
        saveReport(other, museumElevator, "DAMAGED");

        List<Report> mine = reportRepository.findMineWithNodeAndPlace(me.getId());

        assertThat(mine).extracting(Report::getId).containsExactly(mineSecond.getId(), mineFirst.getId());
    }

    @Test
    @DisplayName("노드·층·장소를 fetch join 해서 세션 없이도 장소명과 층을 읽을 수 있다")
    void fetchesNodeFloorAndPlaceEagerly() {
        saveReport(me, museumElevator, "BROKEN");
        entityManager.flush();
        // 영속성 컨텍스트를 비워, 조회 결과가 지연 로딩이 아니라 fetch join으로 채워졌는지 확인한다.
        entityManager.clear();

        List<Report> mine = reportRepository.findMineWithNodeAndPlace(me.getId());
        entityManager.detach(mine.get(0));

        Report report = mine.get(0);
        assertThat(report.getNode().getName()).isEqualTo("본관 엘리베이터");
        assertThat(report.getNode().getFloorMap().getFloorLevel()).isEqualTo(2);
        assertThat(report.getNode().getFloorMap().getPlace().getName()).isEqualTo("국립경주박물관");
    }

    @Test
    @DisplayName("지하층(음수)도 그대로 보존한다")
    void keepsNegativeFloorLevel() {
        saveReport(me, hallRamp, "BLOCKED");

        List<Report> mine = reportRepository.findMineWithNodeAndPlace(me.getId());

        assertThat(mine.get(0).getNode().getFloorMap().getFloorLevel()).isEqualTo(-1);
    }

    @Test
    @DisplayName("제보가 없으면 빈 목록이다")
    void returnsEmptyWhenNoReports() {
        saveReport(other, museumElevator, "BROKEN");

        assertThat(reportRepository.findMineWithNodeAndPlace(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("countByReporter_Id는 내 시설 제보 수만 센다")
    void countsOnlyMine() {
        saveReport(me, museumElevator, "BROKEN");
        saveReport(me, hallRamp, "BLOCKED");
        saveReport(other, hallRamp, "BLOCKED");

        assertThat(reportRepository.countByReporter_Id(me.getId())).isEqualTo(2);
        assertThat(reportRepository.countByReporter_Id(other.getId())).isEqualTo(1);
    }
}
