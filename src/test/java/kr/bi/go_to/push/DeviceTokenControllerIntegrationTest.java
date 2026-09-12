package kr.bi.go_to.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.bi.go_to.model.push.DevicePlatform;
import kr.bi.go_to.model.push.DeviceToken;
import kr.bi.go_to.repository.DeviceTokenRepository;
import kr.bi.go_to.repository.MemberRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DeviceTokenControllerIntegrationTest {

    private static final String TOKEN = "fcm-token-aaa";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DeviceTokenRepository deviceTokenRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    JwtService jwtService;

    String accessToken;

    @BeforeEach
    void setUp() {
        deviceTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        accessToken = login("푸시테스터");
    }

    @Test
    @DisplayName("기기 토큰을 등록하면 204를 반환하고 내 토큰으로 저장된다")
    void registersDeviceToken() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID","appVersion":"0.1.0"}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        DeviceToken saved = deviceTokenRepository.findByToken(TOKEN).orElseThrow();
        assertThat(saved.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(saved.getAppVersion()).isEqualTo("0.1.0");
        assertThat(saved.getLastRegisteredAt()).isNotNull();
        assertThat(saved.getLastLatitude()).isNull();
    }

    @Test
    @DisplayName("같은 토큰을 다시 등록해도 행이 늘지 않는다")
    void registeringSameTokenIsIdempotent() throws Exception {
        String body = """
                {"token":"%s","platform":"IOS"}
                """.formatted(TOKEN);

        mockMvc.perform(register(accessToken, body)).andExpect(status().isNoContent());
        mockMvc.perform(register(accessToken, body)).andExpect(status().isNoContent());

        assertThat(deviceTokenRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("같은 기기를 다른 계정으로 등록하면 토큰의 주인이 바뀐다")
    void reRegisteringWithAnotherAccountMovesOwnership() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID"}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        String otherToken = login("다른사용자");
        mockMvc.perform(register(
                        otherToken,
                        """
                        {"token":"%s","platform":"ANDROID"}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        Long otherMemberId =
                memberRepository.findByNickname("다른사용자").orElseThrow().getId();
        assertThat(deviceTokenRepository.findAll()).hasSize(1);
        assertThat(deviceTokenRepository.findByToken(TOKEN).orElseThrow().isOwnedBy(otherMemberId))
                .isTrue();
    }

    @Test
    @DisplayName("등록할 때 위치를 함께 보내면 마지막 위치로 저장된다")
    void registersWithLocation() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID","latitude":37.5665,"longitude":126.9780}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        DeviceToken saved = deviceTokenRepository.findByToken(TOKEN).orElseThrow();
        assertThat(saved.getLastLatitude()).isEqualTo(37.567);
        assertThat(saved.getLastLongitude()).isEqualTo(126.978);
        assertThat(saved.getLastLocationAt()).isNotNull();
    }

    @Test
    @DisplayName("기기 위치는 소수점 3자리(약 110m)로 줄여서 저장한다 — 원좌표를 남기지 않는다")
    void storesApproximatedLocationOnly() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID","latitude":37.566812345,"longitude":126.977961234}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        DeviceToken saved = deviceTokenRepository.findByToken(TOKEN).orElseThrow();
        assertThat(saved.getLastLatitude()).isEqualTo(37.567);
        assertThat(saved.getLastLongitude()).isEqualTo(126.978);
    }

    @Test
    @DisplayName("뭉갠 위치로도 반경 판정에는 문제가 없다 — 오차가 반경보다 훨씬 작다")
    void approximationStaysWellWithinPushRadius() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID","latitude":37.5664999,"longitude":126.9784999}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        DeviceToken saved = deviceTokenRepository.findByToken(TOKEN).orElseThrow();
        // 위도 0.001도 ≈ 111m, 즉 최대 오차는 약 55m로 발송 반경(기본 300m)보다 훨씬 작다.
        assertThat(Math.abs(saved.getLastLatitude() - 37.5664999) * 111_320).isLessThan(60.0);
        assertThat(Math.abs(saved.getLastLongitude() - 126.9784999) * 88_000).isLessThan(60.0);
    }

    @Test
    @DisplayName("위치를 빼고 다시 등록해도 이전 위치를 지우지 않는다")
    void registeringWithoutLocationKeepsPreviousLocation() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID","latitude":37.5665,"longitude":126.9780}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID"}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        assertThat(deviceTokenRepository.findByToken(TOKEN).orElseThrow().getLastLatitude())
                .isEqualTo(37.567);
    }

    @Test
    @DisplayName("위치만 따로 갱신할 수 있다")
    void updatesLocation() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID"}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/members/me/device-tokens/location")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"token":"%s","latitude":35.8348,"longitude":129.2249}
                                """
                                        .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        assertThat(deviceTokenRepository.findByToken(TOKEN).orElseThrow().getLastLatitude())
                .isEqualTo(35.835);
    }

    @Test
    @DisplayName("남의 기기 위치는 바꿀 수 없다")
    void cannotUpdateAnotherMembersDeviceLocation() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID","latitude":37.5665,"longitude":126.9780}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        String otherToken = login("침입자");
        mockMvc.perform(patch("/api/v1/members/me/device-tokens/location")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"token":"%s","latitude":0.0,"longitude":0.0}
                                """
                                        .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        assertThat(deviceTokenRepository.findByToken(TOKEN).orElseThrow().getLastLatitude())
                .isEqualTo(37.567);
    }

    @Test
    @DisplayName("로그아웃 시 토큰을 해제하면 목록에서 사라진다")
    void unregistersDeviceToken() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID"}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/members/me/device-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("token", TOKEN))
                .andExpect(status().isNoContent());

        assertThat(deviceTokenRepository.findByToken(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("남의 토큰은 해제할 수 없다")
    void cannotUnregisterAnotherMembersToken() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s","platform":"ANDROID"}
                        """
                                .formatted(TOKEN)))
                .andExpect(status().isNoContent());

        String otherToken = login("침입자");
        mockMvc.perform(delete("/api/v1/members/me/device-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .param("token", TOKEN))
                .andExpect(status().isNoContent());

        assertThat(deviceTokenRepository.findByToken(TOKEN)).isPresent();
    }

    @Test
    @DisplayName("등록하지 않은 토큰을 해제해도 204를 반환한다")
    void unregisteringUnknownTokenIsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me/device-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .param("token", "없는토큰"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("토큰이 비어 있으면 400을 반환한다")
    void returns400WhenTokenIsBlank() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"  ","platform":"ANDROID"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("플랫폼이 없으면 400을 반환한다")
    void returns400WhenPlatformIsMissing() throws Exception {
        mockMvc.perform(register(
                        accessToken,
                        """
                        {"token":"%s"}
                        """.formatted(TOKEN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 토큰을 등록하면 401을 반환한다")
    void returns401WithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/members/me/device-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"token":"%s","platform":"ANDROID"}
                                """
                                        .formatted(TOKEN)))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String token, String body) {
        return post("/api/v1/members/me/device-tokens")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String login(String nickname) {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, nickname);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
