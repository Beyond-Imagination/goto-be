package kr.bi.go_to.savedplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
    JwtService jwtService;

    Place place;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        savedPlaceRepository.deleteAll();
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

    private String login(String nickname) {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, nickname);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
