package kr.bi.go_to.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.FacilityNodeRepository;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
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
class AdminFloorMapControllerIntegrationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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
    void 도면을_처음_등록하면_생성된다() throws Exception {
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
    void 같은_장소와_층에_재등록하면_기존_도면을_덮어쓴다() throws Exception {
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
    void 존재하지_않는_장소면_404를_반환한다() throws Exception {
        String token = login("admin");

        mockMvc.perform(put("/api/v1/admin/places/{placeId}/floors/{floor}", 999999L, 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PLACE_NOT_FOUND"));
    }

    @Test
    void 인증_없이_호출하면_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/admin/places/{placeId}/floors/{floor}", place.getId(), 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":[]}"))
                .andExpect(status().isUnauthorized());
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
