package kr.bi.go_to.obstaclereport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
class ObstacleReportControllerIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ObstacleReportRepository obstacleReportRepository;

    @Autowired
    ObstacleReportConfirmationRepository obstacleReportConfirmationRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    MemberRepository memberRepository;

    String reporterToken;

    @BeforeEach
    void setUp() throws Exception {
        obstacleReportConfirmationRepository.deleteAll();
        obstacleReportRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        reporterToken = login("reporter");
    }

    @Test
    @DisplayName("장애물을 제보하면 201과 생성된 리포트를 반환한다")
    void createsObstacleReport() throws Exception {
        mockMvc.perform(post("/api/v1/obstacle-reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueType").value("SIDEWALK_DAMAGE"))
                .andExpect(jsonPath("$.severity").value("CAUTION"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.confirmedCount").value(0))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    @DisplayName("인증 없이 제보하면 401을 반환한다")
    void returns401WhenCreatingWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/obstacle-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("영향받는 이동조건 유형이 비어있으면 400을 반환한다")
    void returns400WhenAffectedMobilityTypesIsEmpty() throws Exception {
        String body =
                """
                {
                  "lat": 37.5665,
                  "lng": 126.9780,
                  "issueType": "SIDEWALK_DAMAGE",
                  "severity": "CAUTION",
                  "affectedMobilityTypes": []
                }
                """;

        mockMvc.perform(post("/api/v1/obstacle-reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 제보를 조회하면 404를 반환한다")
    void returns404WhenGettingNonExistentReport() throws Exception {
        mockMvc.perform(get("/api/v1/obstacle-reports/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("OBSTACLE_REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("\"아직 있어요\"를 누르면 confirmedCount가 증가하고, 같은 회원이 다시 눌러도 중복 증가하지 않는다")
    void confirmingIncrementsCountOncePerMember() throws Exception {
        Long reportId = createReportAndGetId();
        String confirmerToken = login("confirmer");

        mockMvc.perform(post("/api/v1/obstacle-reports/{id}/status", reportId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(confirmerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"action": "STILL_PRESENT"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(1));

        mockMvc.perform(post("/api/v1/obstacle-reports/{id}/status", reportId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(confirmerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"action": "STILL_PRESENT"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(1));
    }

    @Test
    @DisplayName("\"해결됐어요\"를 누르면 상태가 RESOLVED가 된다")
    void resolvingSetsStatusToResolved() throws Exception {
        Long reportId = createReportAndGetId();

        mockMvc.perform(post("/api/v1/obstacle-reports/{id}/status", reportId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"action": "RESOLVED"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("이미 해결된 제보에 상태 변경을 시도하면 409를 반환하고 재오픈되지 않는다")
    void doesNotReopenResolvedReport() throws Exception {
        Long reportId = createReportAndGetId();

        mockMvc.perform(post("/api/v1/obstacle-reports/{id}/status", reportId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"action": "RESOLVED"}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/obstacle-reports/{id}/status", reportId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"action": "STILL_PRESENT"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OBSTACLE_REPORT_ALREADY_RESOLVED"));
    }

    @Test
    @DisplayName("bbox 내 클러스터를 조회하면 집계된 요약을 반환한다")
    void returnsClusterSummaryWithinBbox() throws Exception {
        createReportAndGetId();

        mockMvc.perform(get("/api/v1/obstacle-reports/clusters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .param("minLat", "37.5")
                        .param("minLng", "126.9")
                        .param("maxLat", "37.6")
                        .param("maxLng", "127.1")
                        .param("zoom", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reportCount").value(1))
                .andExpect(jsonPath("$[0].maxSeverity").value("CAUTION"));
    }

    @Test
    @DisplayName("mobilityType으로 클러스터를 필터링하면 해당 이동조건을 포함하지 않는 제보는 제외된다")
    void filtersClustersByMobilityType() throws Exception {
        createReportAndGetId();

        mockMvc.perform(get("/api/v1/obstacle-reports/clusters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .param("minLat", "37.5")
                        .param("minLng", "126.9")
                        .param("maxLat", "37.6")
                        .param("maxLng", "127.1")
                        .param("zoom", "14")
                        .param("mobilityType", "SLOW_WALKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("가까운 줌에서는 클러스터링 없이 제보받은 모든 핀을 그대로 반환한다")
    void doesNotClusterAtCloseZoom() throws Exception {
        createReportAndGetId(37.5665, 126.9780, "CAUTION");
        createReportAndGetId(37.5665, 126.9780, "CAUTION");

        mockMvc.perform(get("/api/v1/obstacle-reports/clusters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .param("minLat", "37.5")
                        .param("minLng", "126.9")
                        .param("maxLat", "37.6")
                        .param("maxLng", "127.1")
                        .param("zoom", "16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reportCount").value(1))
                .andExpect(jsonPath("$[1].reportCount").value(1));
    }

    @Test
    @DisplayName("먼 줌에서는 최대 5개까지만 반환하며 심각도가 높은 클러스터를 우선한다")
    void capsClusterCountAtFarZoomBySeverity() throws Exception {
        createReportAndGetId(30.0, 120.0, "IMPASSABLE");
        createReportAndGetId(32.0, 120.0, "IMPASSABLE");
        createReportAndGetId(34.0, 120.0, "CAUTION");
        createReportAndGetId(36.0, 120.0, "CAUTION");
        createReportAndGetId(38.0, 120.0, "CAUTION");
        createReportAndGetId(40.0, 120.0, "INFO");
        createReportAndGetId(42.0, 120.0, "INFO");

        mockMvc.perform(get("/api/v1/obstacle-reports/clusters")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .param("minLat", "25")
                        .param("minLng", "115")
                        .param("maxLat", "45")
                        .param("maxLng", "125")
                        .param("zoom", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.maxSeverity == 'INFO')]").isEmpty());
    }

    private Long createReportAndGetId() throws Exception {
        return createReportAndGetId(37.5665, 126.9780, "CAUTION");
    }

    private Long createReportAndGetId(double lat, double lng, String severity) throws Exception {
        String body = mockMvc.perform(post("/api/v1/obstacle-reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(lat, lng, severity)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) objectMapper.readValue(body, MAP_TYPE).get("id")).longValue();
    }

    private String createRequestBody() {
        return createRequestBody(37.5665, 126.9780, "CAUTION");
    }

    private String createRequestBody(double lat, double lng, String severity) {
        return """
                {
                  "lat": %s,
                  "lng": %s,
                  "issueType": "SIDEWALK_DAMAGE",
                  "severity": "%s",
                  "affectedMobilityTypes": ["WHEELCHAIR"]
                }
                """
                .formatted(lat, lng, severity);
    }

    private String login(String nickname) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {
                          "nickname": "%s",
                          "password": "password"
                        }
                        """
                                        .formatted(nickname)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return (String) objectMapper.readValue(body, MAP_TYPE).get("accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
