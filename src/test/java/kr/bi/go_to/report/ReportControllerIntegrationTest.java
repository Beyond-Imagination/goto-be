package kr.bi.go_to.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import kr.bi.go_to.model.map.FacilityNode;
import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.repository.ReportRepository;
import kr.bi.go_to.service.JwtService;
import kr.bi.go_to.support.TestMemberAuthentication;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReportControllerIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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
    MemberRepository memberRepository;

    @Autowired
    JwtService jwtService;

    String reporterToken;
    Long nodeId;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        facilityNodeRepository.deleteAll();
        floorMapRepository.deleteAll();
        placeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        reporterToken = TestMemberAuthentication.accessToken(memberRepository, jwtService, "시설제보자");

        Place place = placeRepository.save(Place.builder()
                .externalId("facility-report-place")
                .source("TEST")
                .name("국립경주박물관")
                .sanitizedAddress("경북 경주시 일정로 186")
                .build());
        FloorMap floorMap = floorMapRepository.save(
                FloorMap.builder().place(place).floorLevel(2).build());
        nodeId = facilityNodeRepository
                .save(FacilityNode.builder()
                        .floorMap(floorMap)
                        .nodeType("ELEVATOR")
                        .name("본관 엘리베이터")
                        .geojsonPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(129.2287, 35.8295)))
                        .isCheckpoint(true)
                        .snapRadius(5)
                        .build())
                .getId();
    }

    @Test
    @DisplayName("시설 상태를 제보하면 201과 시설·장소 정보가 함께 담긴 응답을 반환한다")
    void createsFacilityReport() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("BROKEN", "엘리베이터가 멈춰 있어요.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value(nodeId))
                .andExpect(jsonPath("$.nodeType").value("ELEVATOR"))
                .andExpect(jsonPath("$.nodeName").value("본관 엘리베이터"))
                .andExpect(jsonPath("$.floorLevel").value(2))
                .andExpect(jsonPath("$.placeName").value("국립경주박물관"))
                .andExpect(jsonPath("$.issueType").value("BROKEN"))
                .andExpect(jsonPath("$.description").value("엘리베이터가 멈춰 있어요."))
                // 체크포인트 노드라 PDR 보정 정보가 함께 온다.
                .andExpect(jsonPath("$.calibration.snapRadius").value(5));
    }

    @Test
    @DisplayName("정의되지 않은 이슈 유형은 400을 반환하고 저장하지 않는다")
    void rejectsUnknownIssueType() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("우리가 정한 적 없는 값", null)))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(reportRepository.count()).isZero();
    }

    @Test
    @DisplayName("이슈 유형이 없으면 400을 반환한다")
    void rejectsMissingIssueType() throws Exception {
        String body = """
                {
                  "nodeId": %s
                }
                """
                .formatted(nodeId);

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 시설 노드로 제보하면 404를 반환한다")
    void returns404WhenNodeMissing() throws Exception {
        String body =
                """
                {
                  "nodeId": %s,
                  "issueType": "BROKEN"
                }
                """
                        .formatted(nodeId + 9999);

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증 없이 제보하면 401을 반환한다")
    void returns401WithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("BROKEN", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("상세 조회와 내 제보 기록(시설)이 방금 만든 제보를 돌려준다")
    void findsCreatedReport() throws Exception {
        String created = mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("OUT_OF_SERVICE", "  점검 중입니다  ")))
                .andExpect(status().isCreated())
                // 앞뒤 공백은 다듬어 저장한다.
                .andExpect(jsonPath("$.description").value("점검 중입니다"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long reportId = ((Number) objectMapper.readValue(created, MAP_TYPE).get("id")).longValue();

        mockMvc.perform(get("/api/v1/reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueType").value("OUT_OF_SERVICE"))
                .andExpect(jsonPath("$.placeName").value("국립경주박물관"));

        mockMvc.perform(get("/api/v1/members/me/facility-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reportId))
                .andExpect(jsonPath("$[0].nodeName").value("본관 엘리베이터"))
                .andExpect(jsonPath("$[0].floorLevel").value(2))
                .andExpect(jsonPath("$[0].placeName").value("국립경주박물관"))
                .andExpect(jsonPath("$[0].address").value("경북 경주시 일정로 186"));
    }

    @Test
    @DisplayName("빈 메모는 null로 저장된다")
    void blankDescriptionIsStoredAsNull() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("DAMAGED", "   ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value((Object) null));
    }

    @Test
    @DisplayName("없는 제보를 상세 조회하면 404를 반환한다")
    void returns404WhenReportMissing() throws Exception {
        mockMvc.perform(get("/api/v1/reports/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("다른 사람 제보는 내 제보 기록에 나오지 않는다")
    void doesNotLeakOtherMembersReports() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("BROKEN", null)))
                .andExpect(status().isCreated());

        String otherToken = TestMemberAuthentication.accessToken(memberRepository, jwtService, "다른사람");

        mockMvc.perform(get("/api/v1/members/me/facility-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("제보건수 통계에 시설 제보도 포함된다")
    void countsFacilityReportsInActivityStats() throws Exception {
        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.reportCount").value(0));

        mockMvc.perform(post("/api/v1/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("BROKEN", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.reportCount").value(1));
    }

    private String createRequestBody(String issueType, String description) {
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";

        return """
                {
                  "nodeId": %s,
                  "issueType": "%s",
                  "description": %s
                }
                """
                .formatted(nodeId, issueType, descriptionJson);
    }
}
