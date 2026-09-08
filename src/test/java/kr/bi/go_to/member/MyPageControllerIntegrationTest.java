package kr.bi.go_to.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.service.JwtService;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MyPageControllerIntegrationTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ObstacleReportRepository obstacleReportRepository;

    @Autowired
    ObstacleReportConfirmationRepository obstacleReportConfirmationRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    JwtService jwtService;

    Member me;
    String token;

    @BeforeEach
    void setUp() {
        obstacleReportConfirmationRepository.deleteAll();
        obstacleReportRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        MemberPreferences preferences = new MemberPreferences();
        preferences.setMobilityModes(List.of(kr.bi.go_to.enums.MobilityMode.WHEELCHAIR));
        preferences.setInformationPreferences(new MemberPreferences.InformationPreferences(
                List.of(kr.bi.go_to.enums.PriorityFacility.ELEVATOR),
                List.of(kr.bi.go_to.enums.AvoidCondition.STAIRS)));

        me = memberRepository.save(new Member(Role.USER, "마이페이지사용자", 15L, preferences));
        token = jwtService.createAccessToken(me.getId().toString());
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
                .affectedMobilityTypes(java.util.Set.of(MobilityType.WHEELCHAIR))
                .photoUrls(List.of())
                .confirmedCount(confirmedCount)
                .lastConfirmedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("내 프로필 조회는 닉네임과 이동 방식, 활동 통계를 함께 반환한다")
    void returnsProfileWithStats() throws Exception {
        saveReport(me, 5);
        saveReport(me, 3);

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("마이페이지사용자"))
                .andExpect(jsonPath("$.mobilityModes[0]").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.stats.reportCount").value(2))
                .andExpect(jsonPath("$.stats.helpedPeopleCount").value(8))
                .andExpect(jsonPath("$.stats.resolvedConfirmationCount").value(0));
    }

    @Test
    @DisplayName("활동 통계는 다른 회원의 제보를 포함하지 않는다")
    void statsAreScopedToMe() throws Exception {
        Member other = memberRepository.save(new Member(Role.USER, "다른사용자"));
        saveReport(other, 10);
        saveReport(me, 1);

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.reportCount").value(1))
                .andExpect(jsonPath("$.stats.helpedPeopleCount").value(1));
    }

    @Test
    @DisplayName("접근성 프로필 조회는 저장된 preferences를 그대로 반환한다")
    void returnsPreferences() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/preferences").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobilityModes[0]").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.priorityFacilities[0]").value("ELEVATOR"))
                .andExpect(jsonPath("$.avoidConditions[0]").value("STAIRS"));
    }

    @Test
    @DisplayName("접근성 프로필 수정은 전달한 값으로 교체하고 알림·보기 설정은 건드리지 않는다")
    void updatesPreferencesWithoutTouchingSettings() throws Exception {
        mockMvc.perform(
                        put("/api/v1/members/me/settings")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "notifications": {
                                    "savedPlaceStatusChange": true,
                                    "savedPlaceNearbyObstacle": false,
                                    "myReportConfirmed": false,
                                    "myReportConfirmationRequested": false,
                                    "nearbyHelpRequest": false,
                                    "myHelpRequestAccepted": false
                                  },
                                  "display": {
                                    "largeText": true,
                                    "highContrast": false,
                                    "vibration": false,
                                    "statusAlerts": false
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(
                        put("/api/v1/members/me/preferences")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "mobilityModes": ["STROLLER"],
                                  "priorityFacilities": ["RAMP", "PARKING"],
                                  "avoidConditions": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobilityModes[0]").value("STROLLER"))
                .andExpect(jsonPath("$.priorityFacilities.length()").value(2))
                .andExpect(jsonPath("$.avoidConditions.length()").value(0));

        mockMvc.perform(get("/api/v1/members/me/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.savedPlaceStatusChange").value(true))
                .andExpect(jsonPath("$.display.largeText").value(true));

        Member reloaded = memberRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getPreferences().getMobilityModes())
                .containsExactly(kr.bi.go_to.enums.MobilityMode.STROLLER);
    }

    @Test
    @DisplayName("우선 확인 시설이 3개를 넘으면 400을 반환한다")
    void rejectsTooManyPriorityFacilities() throws Exception {
        mockMvc.perform(
                        put("/api/v1/members/me/preferences")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "mobilityModes": [],
                                  "priorityFacilities": ["ELEVATOR", "RAMP", "PARKING", "ACCESSIBLE_TOILET"],
                                  "avoidConditions": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("설정을 저장한 적 없는 회원도 기본값(전부 false)으로 조회된다")
    void returnsDefaultSettingsForUntouchedMember() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.savedPlaceStatusChange").value(false))
                .andExpect(jsonPath("$.notifications.myHelpRequestAccepted").value(false))
                .andExpect(jsonPath("$.display.largeText").value(false))
                .andExpect(jsonPath("$.display.statusAlerts").value(false));
    }

    @Test
    @DisplayName("내 제보 목록은 내가 쓴 제보만 최신순으로 반환한다")
    void returnsMyReportsOnly() throws Exception {
        Member other = memberRepository.save(new Member(Role.USER, "남의제보작성자"));
        saveReport(other, 0);
        ObstacleReport mine = saveReport(me, 4);

        mockMvc.perform(get("/api/v1/members/me/obstacle-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId()))
                .andExpect(jsonPath("$[0].issueType").value("SIDEWALK_DAMAGE"))
                .andExpect(jsonPath("$[0].severity").value("CAUTION"))
                .andExpect(jsonPath("$[0].confirmedCount").value(4))
                .andExpect(jsonPath("$[0].latitude").value(37.5665))
                .andExpect(jsonPath("$[0].longitude").value(126.978));
    }

    @Test
    @DisplayName("내가 확인한 제보 목록은 확인 기록과 대상 제보를 함께 반환한다")
    void returnsMyConfirmedReports() throws Exception {
        Member reporter = memberRepository.save(new Member(Role.USER, "제보작성자"));
        ObstacleReport target = saveReport(reporter, 1);
        obstacleReportConfirmationRepository.save(ObstacleReportConfirmation.builder()
                .obstacleReport(target)
                .member(me)
                .build());

        mockMvc.perform(get("/api/v1/members/me/obstacle-report-confirmations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].report.id").value(target.getId()))
                .andExpect(jsonPath("$[0].confirmedAt").exists());
    }

    @Test
    @DisplayName("인증 없이 마이페이지를 호출하면 401을 반환한다")
    void returns401WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/members/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/settings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/obstacle-reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/obstacle-report-confirmations")).andExpect(status().isUnauthorized());
    }
}
