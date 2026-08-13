package kr.bi.go_to.map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.bi.go_to.model.map.FloorGeoJson;
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
class IndoorMapControllerIntegrationTest {

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
    @DisplayName("등록된 도면을 조회한다")
    void getsRegisteredFloorMap() throws Exception {
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

        mockMvc.perform(get("/api/v1/places/{placeId}/floors/{floor}/indoor-map", place.getId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].properties.node_id").value("elevator-1"));
    }

    @Test
    @DisplayName("존재하지 않는 도면이면 404를 반환한다")
    void returns404WhenFloorMapDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/floors/{floor}/indoor-map", place.getId(), 99)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FLOOR_MAP_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없이 호출하면 401을 반환한다")
    void returns401WhenCalledWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/floors/{floor}/indoor-map", place.getId(), 1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("캐시에 저장된 값이 있으면 DB를 다시 조회하지 않고 재사용한다")
    void reusesCachedValueInsteadOfQueryingDbAgain() throws Exception {
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

        mockMvc.perform(get("/api/v1/places/{placeId}/floors/{floor}/indoor-map", place.getId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.node_id").value("elevator-1"));

        // 캐시가 실제로 재사용되는지 검증하기 위해 서비스 계층(캐시 evict)을 거치지 않고 DB를 직접 변경한다.
        FloorMap floorMap =
                floorMapRepository.findByPlace_IdAndFloorLevel(place.getId(), 1).orElseThrow();
        FloorGeoJson emptyGeoJson = new FloorGeoJson();
        emptyGeoJson.setFeatures(List.of());
        floorMap.replaceGeojsonData(emptyGeoJson);
        floorMapRepository.save(floorMap);

        mockMvc.perform(get("/api/v1/places/{placeId}/floors/{floor}/indoor-map", place.getId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.node_id").value("elevator-1"));
    }

    @Test
    @DisplayName("도면을 재등록하면 캐시가 무효화되어 새 값을 즉시 반환한다")
    void evictsCacheWhenFloorMapIsReRegistered() throws Exception {
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

        mockMvc.perform(get("/api/v1/places/{placeId}/floors/{floor}/indoor-map", place.getId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.node_id").value("elevator-1"));

        mockMvc.perform(
                        put("/api/v1/admin/places/{placeId}/floors/{floor}", place.getId(), 1)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/places/{placeId}/floors/{floor}/indoor-map", place.getId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.node_id").value("toilet-1"));
    }

    private String login(String nickname) {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, nickname);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
