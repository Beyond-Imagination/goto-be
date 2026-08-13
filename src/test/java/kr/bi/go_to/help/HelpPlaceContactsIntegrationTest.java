package kr.bi.go_to.help;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.service.JwtService;
import kr.bi.go_to.support.TestMemberAuthentication;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
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
class HelpPlaceContactsIntegrationTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JwtService jwtService;

    @Test
    void 선택한_장소의_공식_대표전화와_긴급연락처를_반환한다() throws Exception {
        Place place = savePlace("국립경주박물관", 35.8294371, 129.2286552, "054-740-7500", false);
        String token = login();

        mockMvc.perform(get("/api/v1/help-requests/place-contacts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("placeId", place.getId().toString())
                        .param("latitude", "35.8294371")
                        .param("longitude", "129.2286552"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyContact.type").value("EMERGENCY"))
                .andExpect(jsonPath("$.emergencyContact.telephone").value("119"))
                .andExpect(jsonPath("$.placeContacts[0].matchType").value("SELECTED_PLACE"))
                .andExpect(jsonPath("$.placeContacts[0].contactAvailable").value(true))
                .andExpect(jsonPath("$.placeContacts[0].contacts[0].type").value("PLACE_REPRESENTATIVE"))
                .andExpect(jsonPath("$.placeContacts[0].contacts[0].label").value("대표 전화"))
                .andExpect(jsonPath("$.placeContacts[0].contacts[0].telephone").value("054-740-7500"))
                .andExpect(jsonPath("$.placeContacts[0].contacts[0].source").value("TEST"));
    }

    @Test
    void 현재_위치_조회는_거리순으로_활성_장소_후보를_반환한다() throws Exception {
        Place nearer = savePlace("가까운 관광안내소", 10.0001, 10.0001, "02-111-1111", false);
        Place farther = savePlace("조금 먼 공공기관", 10.0008, 10.0008, "02-222-2222", false);
        savePlace("삭제된 장소", 10.00001, 10.00001, "02-333-3333", true);
        String token = login();

        mockMvc.perform(get("/api/v1/help-requests/place-contacts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("latitude", "10.0")
                        .param("longitude", "10.0")
                        .param("radiusMeters", "500")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeContacts.length()").value(2))
                .andExpect(jsonPath("$.placeContacts[0].placeId").value(nearer.getId()))
                .andExpect(jsonPath("$.placeContacts[0].matchType").value("NEARBY_PLACE"))
                .andExpect(jsonPath("$.placeContacts[1].placeId").value(farther.getId()));
    }

    @Test
    void 대표전화가_없으면_장소_연락처를_비활성화한다() throws Exception {
        Place place = savePlace("연락처 없는 장소", 20.0, 20.0, null, false);
        String token = login();

        mockMvc.perform(get("/api/v1/help-requests/place-contacts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("placeId", place.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeContacts[0].contactAvailable").value(false))
                .andExpect(jsonPath("$.placeContacts[0].contacts").isEmpty());
    }

    @Test
    void 장소_ID가_없으면_위도와_경도를_모두_요구한다() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/v1/help-requests/place-contacts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("latitude", "35.8294371"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void 인증_없이_장소_연락처를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/help-requests/place-contacts").param("placeId", "1"))
                .andExpect(status().isUnauthorized());
    }

    private Place savePlace(String name, double latitude, double longitude, String telephone, boolean deleted) {
        return placeRepository.save(Place.builder()
                .externalId(UUID.randomUUID().toString())
                .source("TEST")
                .name(name)
                .sanitizedAddress("테스트 주소")
                .locationPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude)))
                .homepage("https://example.com")
                .tel(telephone)
                .isDeleted(deleted)
                .build());
    }

    private String login() {
        return TestMemberAuthentication.accessToken(memberRepository, jwtService, "help-option-" + UUID.randomUUID());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
