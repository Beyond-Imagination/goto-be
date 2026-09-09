package kr.bi.go_to.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.repository.ReportRepository;
import kr.bi.go_to.service.JwtService;
import kr.bi.go_to.service.obstaclereport.geocoding.NaverReverseGeocodingClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MyPageControllerIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ObstacleReportRepository obstacleReportRepository;

    @Autowired
    ObstacleReportConfirmationRepository obstacleReportConfirmationRepository;

    @Autowired
    PlaceStateReportRepository placeStateReportRepository;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    FacilityNodeRepository facilityNodeRepository;

    @Autowired
    FloorMapRepository floorMapRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    JwtService jwtService;

    /** 테스트에서 NCP를 실제로 호출하지 않도록 리버스 지오코딩만 대역으로 바꾼다. */
    @MockitoBean
    NaverReverseGeocodingClient naverReverseGeocodingClient;

    Member me;
    String token;

    @BeforeEach
    void setUp() {
        obstacleReportConfirmationRepository.deleteAll();
        obstacleReportRepository.deleteAll();
        placeStateReportRepository.deleteAll();
        reportRepository.deleteAll();
        facilityNodeRepository.deleteAll();
        floorMapRepository.deleteAll();
        placeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        MemberPreferences preferences = new MemberPreferences();
        preferences.setMobilityModes(List.of(kr.bi.go_to.enums.MobilityMode.WHEELCHAIR));
        preferences.setInformationPreferences(new MemberPreferences.InformationPreferences(
                List.of(kr.bi.go_to.enums.PriorityFacility.ELEVATOR),
                List.of(kr.bi.go_to.enums.AvoidCondition.STAIRS)));

        when(naverReverseGeocodingClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(java.util.Optional.empty());

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
                .andExpect(jsonPath("$[0].address").doesNotExist())
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
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].report.id").value(target.getId()))
                .andExpect(jsonPath("$.items[0].confirmedAt").exists())
                // 한 건뿐이라 다음 페이지가 없다.
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
    }

    @Test
    @DisplayName("리버스 지오코딩이 성공하면 내 제보 목록에 행정동 주소가 채워진다")
    void fillsAddressFromReverseGeocoding() throws Exception {
        saveReport(me, 0);
        when(naverReverseGeocodingClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(java.util.Optional.of("마포구 상암동"));

        mockMvc.perform(get("/api/v1/members/me/obstacle-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].address").value("마포구 상암동"));
    }

    @Test
    @DisplayName("내가 확인한 리포트에도 같은 방식으로 주소가 채워진다")
    void fillsAddressOnConfirmedReports() throws Exception {
        Member reporter = memberRepository.save(new Member(Role.USER, "주소제보작성자"));
        ObstacleReport target = saveReport(reporter, 1);
        obstacleReportConfirmationRepository.save(ObstacleReportConfirmation.builder()
                .obstacleReport(target)
                .member(me)
                .build());
        when(naverReverseGeocodingClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(java.util.Optional.of("종로구 세종로"));

        mockMvc.perform(get("/api/v1/members/me/obstacle-report-confirmations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].report.address").value("종로구 세종로"));
    }

    private kr.bi.go_to.model.place.Place savePlace(String name) {
        return placeRepository.save(kr.bi.go_to.model.place.Place.builder()
                .externalId(name)
                .source("TEST")
                .name(name)
                .sanitizedAddress(name + " 주소")
                .build());
    }

    private PlaceStateReport savePlaceReport(Member reporter, String placeName) {
        return placeStateReportRepository.saveAndFlush(PlaceStateReport.builder()
                .place(savePlace(placeName))
                .reporter(reporter)
                .accessStatus(PlaceAccessStatus.PARTIALLY_ACCESSIBLE)
                .photoUrls(List.of())
                .build());
    }

    private kr.bi.go_to.model.report.Report saveFacilityReport(Member reporter, String placeName) {
        kr.bi.go_to.model.map.FloorMap floorMap = floorMapRepository.save(kr.bi.go_to.model.map.FloorMap.builder()
                .place(savePlace(placeName))
                .floorLevel(1)
                .build());
        kr.bi.go_to.model.map.FacilityNode node =
                facilityNodeRepository.save(kr.bi.go_to.model.map.FacilityNode.builder()
                        .floorMap(floorMap)
                        .nodeType("ELEVATOR")
                        .name(placeName + " 엘리베이터")
                        .geojsonPoint(point(129.2287, 35.8295))
                        .isCheckpoint(false)
                        .build());

        return reportRepository.saveAndFlush(kr.bi.go_to.model.report.Report.create(node, reporter, "BROKEN", null));
    }

    @Test
    @DisplayName("내 제보 기록은 장애물·장소·시설을 한 목록으로 합쳐 최신순으로 반환한다")
    void mergesThreeKindsInLatestOrder() throws Exception {
        ObstacleReport obstacle = saveReport(me, 1);
        PlaceStateReport place = savePlaceReport(me, "서울숲 공원");
        kr.bi.go_to.model.report.Report facility = saveFacilityReport(me, "성수동 주민센터");

        mockMvc.perform(get("/api/v1/members/me/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                // 저장 순서의 역순(최신순)으로 내려온다.
                .andExpect(jsonPath("$.items[0].kind").value("FACILITY"))
                .andExpect(jsonPath("$.items[0].facility.id").value(facility.getId()))
                .andExpect(jsonPath("$.items[0].obstacle").value((Object) null))
                .andExpect(jsonPath("$.items[1].kind").value("PLACE"))
                .andExpect(jsonPath("$.items[1].place.id").value(place.getId()))
                .andExpect(jsonPath("$.items[2].kind").value("OBSTACLE"))
                .andExpect(jsonPath("$.items[2].obstacle.id").value(obstacle.getId()))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
    }

    @Test
    @DisplayName("kind 필터를 주면 그 분류만 반환한다")
    void filtersByKind() throws Exception {
        saveReport(me, 1);
        PlaceStateReport place = savePlaceReport(me, "필터 공원");
        saveFacilityReport(me, "필터 주민센터");

        mockMvc.perform(get("/api/v1/members/me/reports")
                        .param("kind", "PLACE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].place.id").value(place.getId()));
    }

    @Test
    @DisplayName("size로 페이지를 끊고 nextCursor로 이어 읽으면 모든 제보를 중복 없이 한 번씩 받는다")
    void pagesThroughEveryReportExactlyOnce() throws Exception {
        // 분류를 섞어 5건을 만든다. 커서가 분류별 위치를 따로 들고 다니는지 확인하는 게 핵심이다.
        saveReport(me, 1);
        savePlaceReport(me, "페이지 공원");
        saveFacilityReport(me, "페이지 주민센터");
        saveReport(me, 2);
        savePlaceReport(me, "페이지 공원2");

        java.util.List<String> seen = new java.util.ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            var request = get("/api/v1/members/me/reports")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }

            String body = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            Map<String, Object> parsed = objectMapper.readValue(body, MAP_TYPE);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) parsed.get("items");
            assertThat(items).hasSizeLessThanOrEqualTo(2);
            for (Map<String, Object> item : items) {
                String kind = (String) item.get("kind");
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) item.get(kind.toLowerCase(java.util.Locale.ROOT));
                seen.add(kind + "#" + payload.get("id"));
            }

            cursor = (String) parsed.get("nextCursor");
            if (cursor == null) {
                break;
            }
        }

        assertThat(cursor).as("마지막 페이지에서 커서가 비어야 한다").isNull();
        assertThat(seen).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("커서가 깨졌거나 size가 범위를 벗어나면 400을 반환한다")
    void rejectsBadPagingParameters() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/reports")
                        .param("cursor", "!!not-a-cursor!!")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/members/me/reports")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/members/me/reports")
                        .param("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 회원의 제보는 어떤 분류에서도 내 목록에 섞이지 않는다")
    void doesNotLeakOtherMembersReports() throws Exception {
        Member other = memberRepository.save(new Member(Role.USER, "남의제보전체"));
        saveReport(other, 0);
        savePlaceReport(other, "남의 공원");
        saveFacilityReport(other, "남의 주민센터");

        mockMvc.perform(get("/api/v1/members/me/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
    }

    @Test
    @DisplayName("내가 확인한 리포트도 size·cursor로 끊어 읽고, status로 좁힐 수 있다")
    void pagesAndFiltersConfirmations() throws Exception {
        Member reporter = memberRepository.save(new Member(Role.USER, "확인페이지작성자"));
        ObstacleReport active = saveReport(reporter, 1);
        ObstacleReport resolved = saveReport(reporter, 1);
        resolved.resolve();
        obstacleReportRepository.saveAndFlush(resolved);
        for (ObstacleReport target : List.of(active, resolved)) {
            obstacleReportConfirmationRepository.saveAndFlush(ObstacleReportConfirmation.builder()
                    .obstacleReport(target)
                    .member(me)
                    .build());
        }

        String firstPage = mockMvc.perform(get("/api/v1/members/me/obstacle-report-confirmations")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = (String) objectMapper.readValue(firstPage, MAP_TYPE).get("nextCursor");

        mockMvc.perform(get("/api/v1/members/me/obstacle-report-confirmations")
                        .param("size", "1")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").value((Object) null));

        mockMvc.perform(get("/api/v1/members/me/obstacle-report-confirmations")
                        .param("status", "RESOLVED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].report.id").value(resolved.getId()));
    }

    @Test
    @DisplayName("인증 없이 마이페이지를 호출하면 401을 반환한다")
    void returns401WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/members/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/settings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/obstacle-reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/members/me/obstacle-report-confirmations")).andExpect(status().isUnauthorized());
    }
}
