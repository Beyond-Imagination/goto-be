package kr.bi.go_to.map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class PlaceFloorControllerIntegrationTest {

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
    }

    @Test
    void 등록된_층_목록을_오름차순으로_반환한다() throws Exception {
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
    void 등록된_도면이_없으면_빈_배열을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/floors", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void 인증_없이_호출하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/floors", place.getId())).andExpect(status().isUnauthorized());
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
