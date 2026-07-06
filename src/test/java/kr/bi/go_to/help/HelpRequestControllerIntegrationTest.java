package kr.bi.go_to.help;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import kr.bi.go_to.repository.HelpRequestRejectionRepository;
import kr.bi.go_to.repository.HelpRequestRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
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
class HelpRequestControllerIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    HelpRequestRejectionRepository rejectionRepository;

    @Autowired
    HelpRequestRepository helpRequestRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        rejectionRepository.deleteAll();
        helpRequestRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 장소_없이_길_위_도움_요청을_만들고_수락_전에는_정확한_위치를_숨긴다() throws Exception {
        String requesterToken = login("requester");
        String helperToken = login("helper");
        String strangerToken = login("stranger");

        String createBody = mockMvc.perform(
                        post("/api/v1/help-requests")
                                .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "locationLabel": "국립경주박물관 앞 보도",
                          "latitude": 35.8294371,
                          "longitude": 129.2286552,
                          "floorLevel": 0,
                          "message": "보도 턱 앞에서 이동 도움이 필요해요.",
                          "expiresInMinutes": 30
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.placeId").doesNotExist())
                .andExpect(jsonPath("$.latitude").value(35.8294371))
                .andExpect(jsonPath("$.longitude").value(129.2286552))
                .andExpect(jsonPath("$.emergencyCallRecommended").value(false))
                .andExpect(jsonPath("$.shareMessage").value("현재 국립경주박물관 앞 보도 0층 근처에서 이동 도움이 필요합니다."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID helpRequestId = UUID.fromString(
                (String) objectMapper.readValue(createBody, MAP_TYPE).get("id"));

        mockMvc.perform(get("/api/v1/help-requests/nearby")
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken))
                        .param("latitude", "35.8294")
                        .param("longitude", "129.2286")
                        .param("radiusMeters", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(helpRequestId.toString()))
                .andExpect(jsonPath("$[0].locationLabel").value("국립경주박물관 앞 보도"))
                .andExpect(jsonPath("$[0].latitude").doesNotExist())
                .andExpect(jsonPath("$[0].longitude").doesNotExist());

        mockMvc.perform(get("/api/v1/help-requests/{id}", helpRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.errorMessage").value("접근 권한이 없습니다."));

        mockMvc.perform(post("/api/v1/help-requests/{id}/accept", helpRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.helperNickname").value("helper"))
                .andExpect(jsonPath("$.latitude").value(35.8294371))
                .andExpect(jsonPath("$.longitude").value(129.2286552));

        mockMvc.perform(post("/api/v1/help-requests/{id}/complete", helpRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isString());
    }

    @Test
    void 거절한_도움_요청은_해당_사용자_주변_목록에서_보이지_않는다() throws Exception {
        String requesterToken = login("requester");
        String helperToken = login("helper");

        String createBody = mockMvc.perform(
                        post("/api/v1/help-requests")
                                .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "locationLabel": "경주역 앞",
                          "latitude": 35.8394371,
                          "longitude": 129.2186552
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID helpRequestId = UUID.fromString(
                (String) objectMapper.readValue(createBody, MAP_TYPE).get("id"));

        mockMvc.perform(post("/api/v1/help-requests/{id}/reject", helpRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/help-requests/nearby")
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken))
                        .param("latitude", "35.8394")
                        .param("longitude", "129.2186"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
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
