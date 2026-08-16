package kr.bi.go_to.place;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.service.JwtService;
import kr.bi.go_to.support.TestMemberAuthentication;
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
class NearbyAccessibilitySummaryControllerIntegrationTest {

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

    @Autowired
    JwtService jwtService;

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
    @DisplayName("반경 내 리포트를 severity별로 집계해서 반환한다")
    void aggregatesReportsBySeverityWithinRadius() throws Exception {
        createReport(37.5665, 126.9780, "IMPASSABLE");
        createReport(37.5665, 126.9780, "CAUTION");
        createReport(37.5665, 126.9780, "INFO");

        mockMvc.perform(get("/api/v1/places/nearby-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detourRecommendedCount").value(1))
                .andExpect(jsonPath("$.cautionCount").value(1))
                .andExpect(jsonPath("$.safeCount").value(1))
                .andExpect(jsonPath("$.needsConfirmationCount").value(0));
    }

    @Test
    @DisplayName("반경 밖 리포트는 집계에서 제외한다")
    void excludesReportsOutsideRadius() throws Exception {
        createReport(38.5, 128.0, "IMPASSABLE");

        mockMvc.perform(get("/api/v1/places/nearby-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detourRecommendedCount").value(0));
    }

    @Test
    @DisplayName("RESOLVED 상태 리포트는 집계에서 제외한다")
    void excludesResolvedReports() throws Exception {
        Long reportId = createReport(37.5665, 126.9780, "CAUTION");
        mockMvc.perform(post("/api/v1/obstacle-reports/{id}/status", reportId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"action": "RESOLVED"}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/places/nearby-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cautionCount").value(0));
    }

    @Test
    @DisplayName("좌표가 없으면 400을 반환한다")
    void returns400WhenCoordinatesMissing() throws Exception {
        mockMvc.perform(get("/api/v1/places/nearby-summary").header(HttpHeaders.AUTHORIZATION, bearer(reporterToken)))
                .andExpect(status().isBadRequest());
    }

    private Long createReport(double lat, double lng, String severity) throws Exception {
        String body = mockMvc.perform(post("/api/v1/obstacle-reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "lat": %s,
                                  "lng": %s,
                                  "issueType": "SIDEWALK_DAMAGE",
                                  "severity": "%s",
                                  "affectedMobilityTypes": ["WHEELCHAIR"]
                                }
                                """
                                        .formatted(lat, lng, severity)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return ((Number) objectMapper.readValue(body, MAP_TYPE).get("id")).longValue();
    }

    private String login(String nickname) {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, nickname);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
