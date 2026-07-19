package kr.bi.go_to.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
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
@Import(TestcontainersConfiguration.class)
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

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 임시_로그인하면_액세스_토큰과_리프레시_토큰을_발급한다() throws Exception {
        String responseBody = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "nickname": "tester",
                          "password": "password"
                        }
                        """))
                .andExpect(status().isCreated())
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
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(memberRepository.findByNickname("tester")).isPresent();
    }

    @Test
    void 유효한_리프레시_토큰으로_새_액세스_토큰을_발급한다() throws Exception {
        String loginBody = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "nickname": "tester",
                          "password": "password"
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken =
                (String) objectMapper.readValue(loginBody, MAP_TYPE).get("refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(300));
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
