package kr.bi.go_to.savedplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.repository.SavedPlaceRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SavedPlaceControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SavedPlaceRepository savedPlaceRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PlaceStateReportRepository placeStateReportRepository;

    @Autowired
    JwtService jwtService;

    Place place;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        savedPlaceRepository.deleteAll();
        placeStateReportRepository.deleteAll();
        placeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        place = placeRepository.save(Place.builder()
                .externalId("test-place-1")
                .source("TEST")
                .name("테스트 장소")
                .build());

        token = login("tester");
    }

    @Test
    @DisplayName("장소를 저장하면 204를 반환하고 목록에 나타난다")
    void savesPlaceAndAppearsInList() throws Exception {
        mockMvc.perform(post("/api/v1/places/{id}/save", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placeId").value(place.getId()))
                .andExpect(jsonPath("$[0].name").value("테스트 장소"))
                .andExpect(jsonPath("$[0].isAvailable").value(true));
    }

    @Test
    @DisplayName("이미 저장된 장소를 다시 저장해도 204를 반환하고 중복 저장되지 않는다")
    void savingAlreadySavedPlaceIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/places/{id}/save", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/places/{id}/save", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("존재하지 않는 장소를 저장하려 하면 404를 반환한다")
    void returns404WhenSavingNonExistentPlace() throws Exception {
        mockMvc.perform(post("/api/v1/places/{id}/save", 999_999L).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("저장한 장소를 취소하면 204를 반환하고 목록에서 사라진다")
    void unsavesPlaceAndDisappearsFromList() throws Exception {
        mockMvc.perform(post("/api/v1/places/{id}/save", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/places/{id}/save", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("저장돼 있지 않은 장소를 취소해도 204를 반환한다")
    void unsavingNotSavedPlaceReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/places/{id}/save", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("존재하지 않는 장소를 취소해도 204를 반환한다")
    void unsavingNonExistentPlaceReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/places/{id}/save", 999_999L).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("인증 없이 저장 목록을 조회하면 401을 반환한다")
    void returns401WhenListingWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/saved-places/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("저장하면 그 장소의 상태 변경 알림은 기본으로 켜져 있다")
    void savedPlaceHasNotificationEnabledByDefault() throws Exception {
        save(place, token);

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationEnabled").value(true));
    }

    @Test
    @DisplayName("장소별 알림을 끄면 목록에도 꺼진 상태로 남고, 다시 켜면 되돌아온다")
    void togglesNotificationPerPlace() throws Exception {
        save(place, token);

        mockMvc.perform(patchNotification(place.getId(), token, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.notificationEnabled").value(false));

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationEnabled").value(false));

        mockMvc.perform(patchNotification(place.getId(), token, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationEnabled").value(true));

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationEnabled").value(true));
    }

    @Test
    @DisplayName("장소별 알림 설정은 다른 저장 장소에 영향을 주지 않는다")
    void notificationToggleIsScopedToOnePlace() throws Exception {
        Place other = savePlace("다른 테스트 장소");
        save(place, token);
        save(other, token);

        mockMvc.perform(patchNotification(place.getId(), token, false)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.placeId == %d)].notificationEnabled".formatted(place.getId()))
                        .value(false))
                .andExpect(jsonPath("$[?(@.placeId == %d)].notificationEnabled".formatted(other.getId()))
                        .value(true));
    }

    @Test
    @DisplayName("저장을 해제하면 알림 설정도 사라지고, 다시 저장하면 기본값(켜짐)으로 돌아온다")
    void notificationSettingResetsAfterUnsaveAndSaveAgain() throws Exception {
        save(place, token);
        mockMvc.perform(patchNotification(place.getId(), token, false)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/places/{id}/save", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        save(place, token);

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].notificationEnabled").value(true));
    }

    @Test
    @DisplayName("저장하지 않은 장소의 알림을 바꾸려 하면 404를 반환한다")
    void returns404WhenTogglingNotificationOfNotSavedPlace() throws Exception {
        mockMvc.perform(patchNotification(place.getId(), token, false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SAVED_PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회원이 저장한 장소의 알림은 바꿀 수 없고, 그 회원의 설정도 그대로다")
    void cannotToggleAnotherMembersSavedPlace() throws Exception {
        String otherToken = login("other-tester");
        save(place, otherToken);

        mockMvc.perform(patchNotification(place.getId(), token, false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SAVED_PLACE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].notificationEnabled").value(true));
    }

    @Test
    @DisplayName("다른 회원의 저장 장소는 내 목록에 보이지 않는다")
    void listOnlyContainsMyOwnSavedPlaces() throws Exception {
        String otherToken = login("other-tester");
        save(place, otherToken);

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("알림 on/off 요청에 enabled가 없으면 400을 반환한다")
    void returns400WhenEnabledIsMissing() throws Exception {
        save(place, token);

        mockMvc.perform(patch("/api/v1/saved-places/{placeId}/notification", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 알림을 바꾸려 하면 401을 반환한다")
    void returns401WhenTogglingNotificationWithoutAuth() throws Exception {
        mockMvc.perform(patch("/api/v1/saved-places/{placeId}/notification", place.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("장소 상태 제보가 있으면 가장 최근 제보의 상태와 시각을 함께 반환한다")
    void listCarriesLatestPlaceStateReport() throws Exception {
        Member reporter = memberRepository.save(new Member(Role.USER, "reporter"));
        savePlaceStateReport(place, reporter, PlaceAccessStatus.PARTIALLY_ACCESSIBLE);
        PlaceStateReport latest = savePlaceStateReport(place, reporter, PlaceAccessStatus.INACCESSIBLE);
        save(place, token);

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latestAccessStatus")
                        .value(latest.getAccessStatus().name()))
                .andExpect(jsonPath("$[0].latestReportedAt").exists());
    }

    @Test
    @DisplayName("장소 상태 제보가 없으면 최신 상태는 null이다")
    void listCarriesNullLatestStatusWhenNoReport() throws Exception {
        save(place, token);

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latestAccessStatus").doesNotExist())
                .andExpect(jsonPath("$[0].latestReportedAt").doesNotExist());
    }

    @Test
    @DisplayName("다른 장소의 상태 제보가 내 저장 장소 카드에 섞이지 않는다")
    void latestStatusDoesNotLeakBetweenPlaces() throws Exception {
        Member reporter = memberRepository.save(new Member(Role.USER, "reporter"));
        Place other = savePlace("제보가 있는 다른 장소");
        savePlaceStateReport(other, reporter, PlaceAccessStatus.INACCESSIBLE);
        save(place, token);

        mockMvc.perform(get("/api/v1/saved-places/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placeId").value(place.getId()))
                .andExpect(jsonPath("$[0].latestAccessStatus").doesNotExist());
    }

    private void save(Place target, String accessToken) throws Exception {
        mockMvc.perform(post("/api/v1/places/{id}/save", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());
    }

    private MockHttpServletRequestBuilder patchNotification(Long placeId, String accessToken, boolean enabled) {
        return patch("/api/v1/saved-places/{placeId}/notification", placeId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":%s}".formatted(enabled));
    }

    private Place savePlace(String name) {
        return placeRepository.save(Place.builder()
                .externalId("test-place-" + name)
                .source("TEST")
                .name(name)
                .build());
    }

    private PlaceStateReport savePlaceStateReport(Place target, Member reporter, PlaceAccessStatus accessStatus) {
        return placeStateReportRepository.saveAndFlush(PlaceStateReport.builder()
                .place(target)
                .reporter(reporter)
                .accessStatus(accessStatus)
                .photoUrls(List.of())
                .build());
    }

    private String login(String nickname) {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, nickname);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
