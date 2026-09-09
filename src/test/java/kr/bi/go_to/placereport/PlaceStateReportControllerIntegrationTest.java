package kr.bi.go_to.placereport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
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
class PlaceStateReportControllerIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PlaceStateReportRepository placeStateReportRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JwtService jwtService;

    String reporterToken;
    Long placeId;

    @BeforeEach
    void setUp() {
        placeStateReportRepository.deleteAll();
        placeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        reporterToken = TestMemberAuthentication.accessToken(memberRepository, jwtService, "장소제보자");
        placeId = placeRepository
                .save(Place.builder()
                        .externalId("place-state-report-1")
                        .source("TEST")
                        .name("서울숲 공원")
                        .sanitizedAddress("서울 성동구 뚝섬로 273")
                        .locationPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(127.037, 37.544)))
                        .build())
                .getId();
    }

    @Test
    @DisplayName("장소 상태를 제보하면 201과 장소 정보가 함께 담긴 응답을 반환한다")
    void createsPlaceStateReport() throws Exception {
        mockMvc.perform(post("/api/v1/place-state-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(placeId))
                .andExpect(jsonPath("$.placeName").value("서울숲 공원"))
                .andExpect(jsonPath("$.placeAddress").value("서울 성동구 뚝섬로 273"))
                .andExpect(jsonPath("$.accessStatus").value("PARTIALLY_ACCESSIBLE"))
                .andExpect(jsonPath("$.facilityStatuses.ELEVATOR").value("BROKEN"))
                .andExpect(jsonPath("$.description").value("정문 경사로는 있지만 문이 무거워요"));
    }

    @Test
    @DisplayName("인증 없이 제보하면 401을 반환한다")
    void returns401WithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/place-state-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이용 난이도가 없으면 400을 반환한다")
    void returns400WhenAccessStatusMissing() throws Exception {
        String body = """
                {
                  "placeId": %s
                }
                """
                .formatted(placeId);

        mockMvc.perform(post("/api/v1/place-state-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 장소로 제보하면 404를 반환한다")
    void returns404WhenPlaceMissing() throws Exception {
        String body =
                """
                {
                  "placeId": %s,
                  "accessStatus": "ACCESSIBLE"
                }
                """
                        .formatted(placeId + 9999);

        mockMvc.perform(post("/api/v1/place-state-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("장소별 목록과 상세 조회가 방금 만든 제보를 돌려준다")
    void findsCreatedReport() throws Exception {
        String created = mockMvc.perform(post("/api/v1/place-state-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long reportId = ((Number) objectMapper.readValue(created, MAP_TYPE).get("id")).longValue();

        mockMvc.perform(get("/api/v1/places/{placeId}/state-reports", placeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reportId));

        mockMvc.perform(get("/api/v1/place-state-reports/{id}", reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeName").value("서울숲 공원"));

        // 내 제보 기록의 「장소」 분류도 같은 제보를 돌려준다.
        mockMvc.perform(get("/api/v1/members/me/place-state-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placeName").value("서울숲 공원"));
    }

    @Test
    @DisplayName("없는 제보를 상세 조회하면 404를 반환한다")
    void returns404WhenReportMissing() throws Exception {
        mockMvc.perform(get("/api/v1/place-state-reports/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken))
                .andExpect(status().isNotFound());
    }

    private String createRequestBody() {
        return """
                {
                  "placeId": %s,
                  "accessStatus": "PARTIALLY_ACCESSIBLE",
                  "facilityStatuses": { "ELEVATOR": "BROKEN" },
                  "photoUrls": ["https://cdn.example.test/a.jpg"],
                  "description": "정문 경사로는 있지만 문이 무거워요"
                }
                """
                .formatted(placeId);
    }
}
