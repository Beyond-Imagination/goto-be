package kr.bi.go_to.map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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
class AdminFacilityNodeControllerIntegrationTest {

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
                .andExpect(status().isOk());
    }

    @Test
    void 도면에_존재하는_targetFeatureId로_노드를_등록한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/places/{placeId}/floors/{floor}/nodes", place.getId(), 1)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "targetFeatureId": "elevator-1",
                          "nodeType": "ELEVATOR",
                          "name": "1층 엘리베이터",
                          "lat": 37.579,
                          "lng": 126.977,
                          "isCheckpoint": true,
                          "snapRadius": 5
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetFeatureId").value("elevator-1"))
                .andExpect(jsonPath("$.nodeType").value("ELEVATOR"))
                .andExpect(jsonPath("$.isCheckpoint").value(true))
                .andExpect(jsonPath("$.snapRadius").value(5));
    }

    @Test
    void 도면에_없는_targetFeatureId면_400을_반환한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/places/{placeId}/floors/{floor}/nodes", place.getId(), 1)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "targetFeatureId": "elevator-typo",
                          "nodeType": "ELEVATOR",
                          "lat": 37.579,
                          "lng": 126.977
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TARGET_FEATURE_NOT_FOUND"));
    }

    @Test
    void targetFeatureId가_없어도_등록된다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/places/{placeId}/floors/{floor}/nodes", place.getId(), 1)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "nodeType": "TOILET",
                          "lat": 37.579,
                          "lng": 126.977
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetFeatureId").doesNotExist());
    }

    @Test
    void 존재하지_않는_도면이면_404를_반환한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/places/{placeId}/floors/{floor}/nodes", place.getId(), 99)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                          "nodeType": "TOILET",
                          "lat": 37.579,
                          "lng": 126.977
                        }
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FLOOR_MAP_NOT_FOUND"));
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
