package kr.bi.go_to.map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlaceFloorControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FacilityNodeRepository facilityNodeRepository;

    @Autowired
    FloorMapRepository floorMapRepository;

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
        facilityNodeRepository.deleteAll();
        floorMapRepository.deleteAll();
        placeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        place = placeRepository.save(Place.builder()
                .externalId("test-place-1")
                .source("TEST")
                .name("테스트 장소")
                .build());

        token = login("admin");
    }

    @Test
    @DisplayName("등록된 층 목록을 오름차순으로 반환한다")
    void returnsRegisteredFloorLevelsInAscendingOrder() throws Exception {
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(2).build());
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(-1).build());
        floorMapRepository.save(FloorMap.builder().place(place).floorLevel(1).build());

        mockMvc.perform(get("/api/v1/places/{placeId}/floors", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(-1))
                .andExpect(jsonPath("$[1]").value(1))
                .andExpect(jsonPath("$[2]").value(2));
    }

    @Test
    @DisplayName("등록된 도면이 없으면 빈 배열을 반환한다")
    void returnsEmptyArrayWhenNoFloorMapsExist() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/floors", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("인증 없이 호출하면 401을 반환한다")
    void returns401WhenCalledWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/floors", place.getId())).andExpect(status().isUnauthorized());
    }

    private String login(String nickname) {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, nickname);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
