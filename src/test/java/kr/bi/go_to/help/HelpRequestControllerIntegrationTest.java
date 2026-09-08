package kr.bi.go_to.help;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import kr.bi.go_to.properties.CacheProperties;
import kr.bi.go_to.repository.HelpRequestRejectionRepository;
import kr.bi.go_to.repository.HelpRequestRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.service.JwtService;
import kr.bi.go_to.support.TestMemberAuthentication;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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

    @Autowired
    JwtService jwtService;

    @Autowired
    CacheProperties cacheProperties;

    @Autowired(required = false)
    CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache(cacheProperties.getCacheName("help-requests-pending-count"));
            if (cache != null) {
                cache.clear();
            }
        }
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
                          "kinds": ["MOBILITY_ASSIST", "DOOR_ASSIST"],
                          "expiresInMinutes": 30
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.placeId").doesNotExist())
                .andExpect(jsonPath("$.latitude").value(35.8294371))
                .andExpect(jsonPath("$.longitude").value(129.2286552))
                .andExpect(jsonPath("$.kinds", containsInAnyOrder("MOBILITY_ASSIST", "DOOR_ASSIST")))
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
                .andExpect(jsonPath("$[0].kinds", containsInAnyOrder("MOBILITY_ASSIST", "DOOR_ASSIST")))
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

        // 요청자는 도우미의 수락을 무를 수 없다.
        mockMvc.perform(post("/api/v1/help-requests/{id}/cancel-accept", helpRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ONLY_HELPER_CAN_CANCEL_ACCEPT"));

        // 수락한 도우미가 무르면 요청이 다시 열린 상태가 된다.
        mockMvc.perform(post("/api/v1/help-requests/{id}/cancel-accept", helpRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.helperNickname").doesNotExist())
                .andExpect(jsonPath("$.acceptedAt").doesNotExist());

        mockMvc.perform(post("/api/v1/help-requests/{id}/accept", helpRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

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
                          "longitude": 129.2186552,
                          "kinds": ["WAYFINDING"]
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

    @Test
    void 대기_중인_도움_요청_건수를_정상적으로_조회한다() throws Exception {
        String requesterToken = login("requester");
        String helperToken = login("helper");

        createHelpRequest(requesterToken, "첫 번째 요청");
        createHelpRequest(requesterToken, "두 번째 요청");

        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(2));
    }

    @Test
    void 대기_중인_도움_요청이_없으면_0건을_반환한다() throws Exception {
        String helperToken = login("helper");

        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(0));
    }

    @Test
    void 본인이_생성한_요청만_있으면_0건을_반환한다() throws Exception {
        String requesterToken = login("requester");

        createHelpRequest(requesterToken, "본인 요청");

        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(0));
    }

    @Test
    void TTL_내_재조회_시_캐시된_값을_반환한다() throws Exception {
        String requesterToken = login("requester");
        String helperToken = login("helper");

        createHelpRequest(requesterToken, "첫 번째 요청");

        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(1));

        // 서비스 캐시 무효화를 거치지 않고 추가 요청을 DB에 생성
        createHelpRequest(requesterToken, "두 번째 요청");

        // 캐시 히트로 인해 1이 반환되는지 확인
        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(1));
    }

    @Test
    void 사용자별로_독립된_캐시_키가_적용되어_서로의_카운트에_영향을_주지_않는다() throws Exception {
        String requesterToken = login("requester");
        String helperToken = login("helper");

        createHelpRequest(requesterToken, "요청자의 요청");

        // 요청자는 본인 요청이 제외되어 0건
        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requesterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(0));

        // 도우미는 1건
        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(1));
    }

    @Test
    void 인증_헤더_없이_호출하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/help-requests/pending-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errorMessage").value("인증이 필요합니다."));
    }

    @Test
    void 유효하지_않은_토큰으로_호출하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer("invalid.jwt.token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void 존재하지_않는_회원_토큰으로_호출하면_401을_반환한다() throws Exception {
        String ghostToken = jwtService.createAccessToken("999999");

        mockMvc.perform(get("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ghostToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 지원하지_않는_HTTP_메서드로_호출하면_405를_반환한다() throws Exception {
        String helperToken = login("helper");

        mockMvc.perform(post("/api/v1/help-requests/pending-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(helperToken)))
                .andExpect(status().isMethodNotAllowed());
    }

    private void createHelpRequest(String token, String label) throws Exception {
        mockMvc.perform(post("/api/v1/help-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                """
                        {
                          "locationLabel": "%s",
                          "latitude": 35.8394371,
                          "longitude": 129.2186552,
                          "kinds": ["MOBILITY_ASSIST"],
                          "expiresInMinutes": 30
                        }
                        """,
                                label)))
                .andExpect(status().isCreated());
    }

    private String login(String nickname) {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, nickname);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
