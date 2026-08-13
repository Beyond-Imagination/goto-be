package kr.bi.go_to.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import kr.bi.go_to.model.member.OAuthUser;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.OAuthUserRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.support.OAuthIdentityTestConfiguration;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OAuthIdentityTestConfiguration.class})
class AuthControllerIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    OAuthUserRepository oauthUserRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        oauthUserRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 등록된_OAuth_계정으로_로그인하면_플랫폼_토큰을_발급한다() throws Exception {
        String signupBody = signup("KAKAO", "existing-account", "tester", 15);

        assertThat((String) objectMapper.readValue(signupBody, MAP_TYPE).get("status"))
                .isEqualTo("AUTHENTICATED");

        String responseBody = mockMvc.perform(
                        post("/api/v1/auth/oauth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "provider": "KAKAO",
                          "providerAccessToken": "existing-account"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> response = objectMapper.readValue(responseBody, MAP_TYPE);
        assertThat((String) response.get("accessToken")).contains(".");
        assertThat((String) response.get("refreshToken")).contains(".");
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        assertThat(memberRepository.findByNickname("tester")).isPresent();
    }

    @Test
    void 등록되지_않은_OAuth_계정은_가입_필요_상태를_반환한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/oauth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "provider": "GOOGLE",
                          "providerAccessToken": "new-account"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGN_UP_REQUIRED"))
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.suggestedNickname").value("추천닉네임"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        assertThat(memberRepository.count()).isZero();
        assertThat(oauthUserRepository.count()).isZero();
    }

    @Test
    void OAuth_회원가입을_완료하면_사용자와_연결을_생성하고_플랫폼_토큰을_발급한다() throws Exception {
        String signupBody = signup("NAVER", "new-account", "tester", 31);

        String refreshToken =
                (String) objectMapper.readValue(signupBody, MAP_TYPE).get("refreshToken");

        assertThat(memberRepository.findByNickname("tester"))
                .isPresent()
                .get()
                .extracting(member -> member.getAgreementMask())
                .isEqualTo(31L);
        assertThat(oauthUserRepository.findByProviderAndProviderId(
                        kr.bi.go_to.enums.OAuthProvider.NAVER, "naver-new-account"))
                .isPresent()
                .get()
                .extracting(OAuthUser::getMember)
                .extracting(member -> member.getNickname())
                .isEqualTo("tester");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(300));
    }

    @Test
    void 필수_약관이_누락된_OAuth_회원가입은_거절한다() throws Exception {
        signupRequest("KAKAO", "missing-agreement", "tester", 7)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUIRED_AGREEMENTS_NOT_ACCEPTED"));
    }

    @Test
    void 이미_가입된_OAuth_계정으로_가입을_재시도하면_로그인_재시도를_안내한다() throws Exception {
        signup("KAKAO", "same-account", "first", 15);

        signupRequest("KAKAO", "same-account", "second", 15)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OAUTH_SIGNUP_ALREADY_COMPLETED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        mockMvc.perform(
                        post("/api/v1/auth/oauth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "provider": "KAKAO",
                                  "providerAccessToken": "same-account"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"));

        assertThat(memberRepository.count()).isOne();
        assertThat(oauthUserRepository.count()).isOne();
    }

    @Test
    void 동일한_OAuth_계정의_동시_가입은_한_요청만_성공한다() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<String> first = executorService.submit(() -> concurrentSignup(ready, start, "first"));
            Future<String> second = executorService.submit(() -> concurrentSignup(ready, start, "second"));

            ready.await();
            start.countDown();

            List<Map<String, Object>> responses = List.of(
                    objectMapper.readValue(first.get(), MAP_TYPE), objectMapper.readValue(second.get(), MAP_TYPE));

            assertThat(responses)
                    .filteredOn(response -> "AUTHENTICATED".equals(response.get("status")))
                    .hasSize(1);
            assertThat(responses)
                    .filteredOn(response -> "OAUTH_SIGNUP_ALREADY_COMPLETED".equals(response.get("errorCode")))
                    .hasSize(1);
        }

        assertThat(memberRepository.count()).isOne();
        assertThat(oauthUserRepository.count()).isOne();
    }

    @Test
    void 이미_사용_중인_닉네임으로는_다른_OAuth_계정을_가입할_수_없다() throws Exception {
        signup("KAKAO", "first-account", "tester", 15);

        signupRequest("GOOGLE", "second-account", "tester", 15)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NICKNAME_ALREADY_IN_USE"));
    }

    @Test
    void 유효하지_않은_OAuth_토큰은_표준_에러_응답으로_거절한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/oauth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "provider": "KAKAO",
                          "providerAccessToken": "invalid-token"
                        }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OAUTH_TOKEN"));
    }

    private String signup(String provider, String providerAccessToken, String nickname, long agreementMask)
            throws Exception {
        return signupRequest(provider, providerAccessToken, nickname, agreementMask)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions signupRequest(
            String provider, String providerAccessToken, String nickname, long agreementMask) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/oauth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {
                          "provider": "%s",
                          "providerAccessToken": "%s",
                          "nickname": "%s",
                          "agreementMask": %d,
                          "preferences": {
                            "mobilityModes": [],
                            "informationPreferences": {
                              "priorityFacilities": [],
                              "avoidConditions": []
                            }
                          }
                        }
                        """
                                .formatted(provider, providerAccessToken, nickname, agreementMask)));
    }

    private String concurrentSignup(CountDownLatch ready, CountDownLatch start, String nickname) throws Exception {
        ready.countDown();
        start.await();

        return signupRequest("KAKAO", "concurrent-account", nickname, 15)
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void 유효하지_않은_리프레시_토큰은_표준_에러_응답으로_거절한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "refreshToken": "not-a-jwt"
                        }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.errorMessage").value("유효하지 않은 리프레시 토큰입니다."));
    }
}
