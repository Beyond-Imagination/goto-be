package kr.bi.go_to.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminFloorMapControllerIntegrationTest {

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

    @BeforeEach
    void setUp() {
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
    }

    @Test
    @DisplayName("도면을 처음 등록하면 생성된다")
    void createsFloorMapWhenRegisteredFirstTime() throws Exception {
        String token = login("admin");

        mockMvc.perform(
                        put("/api/v1/admin/places/{placeId}/floors/{floor}", place.getId(), 1)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "type": "FeatureCollection",
                          "features": [
                            {"type":"Feature","geometry":{"type":"Point","coordinates":[126.977,37.579]},"properties":{"node_id":"elevator-1"}}
                          ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.floorLevel").value(1))
                .andExpect(
                        jsonPath("$.geojsonData.features[0].properties.node_id").value("elevator-1"));

        assertThat(floorMapRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("같은 장소와 층에 재등록하면 기존 도면을 덮어쓴다")
    void overwritesExistingFloorMapWhenReRegisteredForSamePlaceAndFloor() throws Exception {
        String firstToken = login("first-admin");

        mockMvc.perform(put("/api/v1/admin/places/{placeId}/floors/{floor}", place.getId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":[]}"))
                .andExpect(status().isOk());

        List<FloorMap> afterFirstPut = floorMapRepository.findAll();
        assertThat(afterFirstPut).hasSize(1);
        Long floorMapId = afterFirstPut.get(0).getId();
        Long firstCreatedById = afterFirstPut.get(0).getCreatedBy().getId();

        String secondToken = login("second-admin");

        mockMvc.perform(
                        put("/api/v1/admin/places/{placeId}/floors/{floor}", place.getId(), 1)
                                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "type": "FeatureCollection",
                          "features": [
                            {"type":"Feature","geometry":{"type":"Point","coordinates":[1,1]},"properties":{"node_id":"toilet-1"}}
                          ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.geojsonData.features[0].properties.node_id").value("toilet-1"));

        List<FloorMap> afterSecondPut = floorMapRepository.findAll();
        assertThat(afterSecondPut).hasSize(1);
        assertThat(afterSecondPut.get(0).getId()).isEqualTo(floorMapId);
        assertThat(afterSecondPut.get(0).getCreatedBy().getId()).isEqualTo(firstCreatedById);
    }

    @Test
    @DisplayName("존재하지 않는 장소면 404를 반환한다")
    void returns404WhenPlaceDoesNotExist() throws Exception {
        String token = login("admin");

        mockMvc.perform(put("/api/v1/admin/places/{placeId}/floors/{floor}", 999999L, 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401을 반환한다")
    void returns401WhenCalledWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/admin/places/{placeId}/floors/{floor}", place.getId(), 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String nickname) {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, nickname);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
