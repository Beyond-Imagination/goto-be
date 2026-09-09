package kr.bi.go_to.placereport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.placereport.PlaceFacilityStatus;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 상태 제보의 QueryDSL 쿼리(작성자 필터 · 장소 필터 · 최신순 정렬 · limit · fetch join)가
 * 실제로 의도한 대로 동작하는지 검증한다.
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
class PlaceStateReportQueryDslRepositoryTest {

    @Autowired
    PlaceStateReportRepository placeStateReportRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    /** 테스트 간 닉네임·외부 ID 유니크 제약 충돌을 막기 위한 일련번호. */
    private static int sequence = 0;

    Member me;
    Member other;
    Place seoulForest;
    Place cityHall;

    @BeforeEach
    void setUp() {
        placeStateReportRepository.deleteAll();
        placeRepository.deleteAll();
        memberRepository.deleteAll();

        sequence += 1;
        me = memberRepository.save(new Member(Role.USER, "장소나" + sequence));
        other = memberRepository.save(new Member(Role.USER, "장소남" + sequence));
        seoulForest = savePlace("서울숲 공원");
        cityHall = savePlace("시청");
    }

    private Place savePlace(String name) {
        return placeRepository.save(Place.builder()
                .externalId(name + "-" + sequence)
                .source("TEST")
                .name(name)
                .sanitizedAddress(name + " 주소")
                .build());
    }

    private PlaceStateReport saveReport(Member reporter, Place place, PlaceAccessStatus accessStatus) {
        Map<PriorityFacility, PlaceFacilityStatus> facilityStatuses = new EnumMap<>(PriorityFacility.class);
        facilityStatuses.put(PriorityFacility.ELEVATOR, PlaceFacilityStatus.AVAILABLE);

        return placeStateReportRepository.saveAndFlush(PlaceStateReport.builder()
                .place(place)
                .reporter(reporter)
                .accessStatus(accessStatus)
                .facilityStatuses(facilityStatuses)
                .photoUrls(List.of("https://cdn.example.test/a.jpg"))
                .description("메모")
                .build());
    }

    @Test
    @DisplayName("findMineWithPlace는 내 제보만 최신순으로 돌려주고 장소를 함께 가져온다")
    void findMineWithPlaceReturnsOnlyMine() {
        PlaceStateReport mineFirst = saveReport(me, seoulForest, PlaceAccessStatus.ACCESSIBLE);
        PlaceStateReport mineSecond = saveReport(me, cityHall, PlaceAccessStatus.INACCESSIBLE);
        saveReport(other, seoulForest, PlaceAccessStatus.PARTIALLY_ACCESSIBLE);

        List<PlaceStateReport> mine = placeStateReportRepository.findMineWithPlace(me.getId());

        assertThat(mine).extracting(PlaceStateReport::getId).containsExactly(mineSecond.getId(), mineFirst.getId());
        // fetch join이 걸려 있으므로 트랜잭션 안에서 장소명이 바로 읽힌다.
        assertThat(mine).extracting(report -> report.getPlace().getName()).containsExactly("시청", "서울숲 공원");
    }

    @Test
    @DisplayName("findMineWithPlace는 제보가 없으면 빈 목록이다")
    void findMineWithPlaceReturnsEmptyWhenNoReports() {
        assertThat(placeStateReportRepository.findMineWithPlace(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("findLatestByPlace는 해당 장소의 제보만 최신순으로 돌려준다")
    void findLatestByPlaceFiltersByPlace() {
        PlaceStateReport forestFirst = saveReport(me, seoulForest, PlaceAccessStatus.ACCESSIBLE);
        PlaceStateReport forestSecond = saveReport(other, seoulForest, PlaceAccessStatus.INACCESSIBLE);
        saveReport(me, cityHall, PlaceAccessStatus.PARTIALLY_ACCESSIBLE);

        List<PlaceStateReport> reports = placeStateReportRepository.findLatestByPlace(seoulForest.getId(), 20);

        assertThat(reports)
                .extracting(PlaceStateReport::getId)
                .containsExactly(forestSecond.getId(), forestFirst.getId());
    }

    @Test
    @DisplayName("findLatestByPlace는 limit만큼만 돌려준다")
    void findLatestByPlaceRespectsLimit() {
        saveReport(me, seoulForest, PlaceAccessStatus.ACCESSIBLE);
        PlaceStateReport latest = saveReport(other, seoulForest, PlaceAccessStatus.INACCESSIBLE);

        List<PlaceStateReport> reports = placeStateReportRepository.findLatestByPlace(seoulForest.getId(), 1);

        assertThat(reports).extracting(PlaceStateReport::getId).containsExactly(latest.getId());
    }

    @Test
    @DisplayName("findByIdWithPlace는 장소를 함께 가져오고, 없는 ID면 빈 Optional이다")
    void findByIdWithPlace() {
        PlaceStateReport saved = saveReport(me, seoulForest, PlaceAccessStatus.PARTIALLY_ACCESSIBLE);

        Optional<PlaceStateReport> found = placeStateReportRepository.findByIdWithPlace(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getPlace().getName()).isEqualTo("서울숲 공원");
        assertThat(found.get().getFacilityStatuses())
                .containsEntry(PriorityFacility.ELEVATOR, PlaceFacilityStatus.AVAILABLE);
        assertThat(placeStateReportRepository.findByIdWithPlace(saved.getId() + 9999))
                .isEmpty();
    }

    @Test
    @DisplayName("countByReporter_Id는 내 장소 제보 수만 센다")
    void countByReporter() {
        saveReport(me, seoulForest, PlaceAccessStatus.ACCESSIBLE);
        saveReport(me, cityHall, PlaceAccessStatus.ACCESSIBLE);
        saveReport(other, seoulForest, PlaceAccessStatus.ACCESSIBLE);

        assertThat(placeStateReportRepository.countByReporter_Id(me.getId())).isEqualTo(2);
    }
}
