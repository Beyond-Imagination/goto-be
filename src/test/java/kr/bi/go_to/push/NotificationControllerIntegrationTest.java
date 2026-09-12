package kr.bi.go_to.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import kr.bi.go_to.controller.placereport.request.CreatePlaceStateReportRequest;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.push.Notification;
import kr.bi.go_to.model.savedplace.SavedPlace;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.NotificationRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.repository.RefreshTokenRepository;
import kr.bi.go_to.repository.SavedPlaceRepository;
import kr.bi.go_to.service.JwtService;
import kr.bi.go_to.service.placereport.PlaceStateReportService;
import kr.bi.go_to.service.push.NotificationService;
import kr.bi.go_to.service.push.PushMessage;
import kr.bi.go_to.service.push.PushNotificationType;
import kr.bi.go_to.support.PushTestConfiguration;
import kr.bi.go_to.support.RecordingPushSender;
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

/**
 * 알림 이력 API.
 *
 * <p>"푸시를 못 받았어도 앱에서 다시 볼 수 있어야 한다"가 이 기능의 존재 이유라, 기기 토큰이
 * 없는 경우까지 포함해 무엇이 남고 무엇이 안 남는지를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PushTestConfiguration.class})
class NotificationControllerIntegrationTest {

    private static final String PATH = "/api/v1/members/me/notifications";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NotificationService notificationService;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    PlaceStateReportService placeStateReportService;

    @Autowired
    RecordingPushSender pushSender;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    SavedPlaceRepository savedPlaceRepository;

    @Autowired
    PlaceStateReportRepository placeStateReportRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    JwtService jwtService;

    Member me;
    String token;

    @BeforeEach
    void setUp() {
        pushSender.clear();
        notificationRepository.deleteAll();
        placeStateReportRepository.deleteAll();
        savedPlaceRepository.deleteAll();
        placeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();

        me = memberRepository.save(new Member(Role.USER, "알림받는사람", 15L, MemberPreferences.empty()));
        token = jwtService.createAccessToken(me.getId().toString());
    }

    @Test
    @DisplayName("저장한 장소에 제보가 올라오면 알림 이력이 남고 목록에 보인다")
    void recordsNotificationFromReport() throws Exception {
        Member reporter = memberRepository.save(new Member(Role.USER, "제보자"));
        Place place = savePlace("국립중앙박물관");
        save(me, place);

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("SAVED_PLACE_STATUS_CHANGE"))
                .andExpect(jsonPath("$.items[0].title").value("저장한 장소에 새 소식이 있어요"))
                .andExpect(jsonPath("$.items[0].body").value("국립중앙박물관 · 일부 불편했어요"))
                .andExpect(jsonPath("$.items[0].route").value("/(tabs)/saved"))
                .andExpect(jsonPath("$.items[0].placeId").value(place.getId()))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("기기 토큰이 없어 푸시를 못 받아도 이력은 남는다")
    void recordsEvenWithoutDeviceToken() throws Exception {
        Member reporter = memberRepository.save(new Member(Role.USER, "제보자"));
        Place place = savePlace("서울숲");
        save(me, place);

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        // 토큰이 없으니 발송은 한 건도 없다.
        assertThat(pushSender.sent()).isEmpty();
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("알림 설정을 꺼 둔 종류는 이력도 남지 않는다")
    void doesNotRecordWhenSettingIsOff() throws Exception {
        MemberPreferences off = MemberPreferences.empty();
        off.getNotificationSettings().setSavedPlaceStatusChange(false);
        me.updatePreferences(off);
        memberRepository.save(me);

        Member reporter = memberRepository.save(new Member(Role.USER, "제보자"));
        Place place = savePlace("광화문광장");
        save(me, place);

        placeStateReportService.create(reporter.getId(), placeStateReport(place));

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("목록은 최신순이고, 커서로 이어 읽으면 겹치거나 빠지지 않는다")
    void paginatesInReverseChronologicalOrder() throws Exception {
        record(5);

        JsonNode first = read(mockMvc.perform(
                        get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()).param("size", "2"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(first.get("items")).hasSize(2);
        assertThat(bodies(first)).containsExactly("알림 5", "알림 4");
        assertThat(first.get("nextCursor").isNull()).isFalse();

        JsonNode second = read(mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("size", "2")
                        .param("cursor", first.get("nextCursor").asText()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(bodies(second)).containsExactly("알림 3", "알림 2");

        JsonNode third = read(mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("size", "2")
                        .param("cursor", second.get("nextCursor").asText()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(bodies(third)).containsExactly("알림 1");
        assertThat(third.get("nextCursor").isNull()).isTrue();
    }

    @Test
    @DisplayName("깨진 커서는 400을 반환한다")
    void returns400ForBrokenCursor() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()).param("cursor", "!!not-a-cursor!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_NOTIFICATION_CURSOR"));
    }

    @Test
    @DisplayName("읽음 처리하면 안 읽은 수가 줄고 목록에도 반영된다")
    void marksOneAsRead() throws Exception {
        record(2);
        Long id = notificationRepository.findAll().get(0).getId();

        mockMvc.perform(patch(PATH + "/{id}/read", id).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PATH + "/unread-count").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        assertThat(notificationRepository.findById(id).orElseThrow().isRead()).isTrue();
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽어도 처음 읽은 시각은 바뀌지 않는다")
    void keepsFirstReadAt() throws Exception {
        record(1);
        Long id = notificationRepository.findAll().get(0).getId();

        mockMvc.perform(patch(PATH + "/{id}/read", id).header(HttpHeaders.AUTHORIZATION, bearer()));
        var firstReadAt = notificationRepository.findById(id).orElseThrow().getReadAt();

        mockMvc.perform(patch(PATH + "/{id}/read", id).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.findById(id).orElseThrow().getReadAt())
                .isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("남의 알림은 읽음 처리할 수 없다")
    void cannotReadAnotherMembersNotification() throws Exception {
        Member other = memberRepository.save(new Member(Role.USER, "다른사람"));
        notificationService.record(other.getId(), message("남의 알림"));
        Long id = notificationRepository.findAll().get(0).getId();

        mockMvc.perform(patch(PATH + "/{id}/read", id).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_NOT_FOUND"));
        assertThat(notificationRepository.findById(id).orElseThrow().isRead()).isFalse();
    }

    @Test
    @DisplayName("모두 읽음 처리하면 안 읽은 수가 0이 된다")
    void marksAllAsRead() throws Exception {
        record(3);

        mockMvc.perform(patch(PATH + "/read").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PATH + "/unread-count").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("모두 읽음은 내 알림만 건드린다")
    void markAllReadIsScopedToMe() throws Exception {
        Member other = memberRepository.save(new Member(Role.USER, "다른사람"));
        notificationService.record(other.getId(), message("남의 알림"));
        record(1);

        mockMvc.perform(patch(PATH + "/read").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());

        assertThat(notificationService.countUnread(other.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 사람의 알림은 내 목록에 보이지 않는다")
    void listOnlyContainsMine() throws Exception {
        Member other = memberRepository.save(new Member(Role.USER, "다른사람"));
        notificationService.record(other.getId(), message("남의 알림"));

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("인증 없이 알림 목록을 조회하면 401을 반환한다")
    void returns401WithoutAuth() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    private void record(int count) {
        for (int index = 1; index <= count; index++) {
            notificationService.record(me.getId(), message("알림 " + index));
        }
    }

    private PushMessage message(String body) {
        return PushMessage.of(PushNotificationType.MY_REPORT_CONFIRMED, "제목", body)
                .route("/report/detail")
                .data("id", 7)
                .build();
    }

    /** 컨텍스트에 ObjectMapper 빈이 없어(웹 변환기만 등록) 테스트용 인스턴스를 따로 쓴다. */
    private JsonNode read(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    private List<String> bodies(JsonNode page) {
        return page.get("items").findValuesAsText("body");
    }

    private Place savePlace(String name) {
        return placeRepository.save(Place.builder()
                .externalId("notification-test-" + name)
                .source("TEST")
                .name(name)
                .build());
    }

    private void save(Member member, Place place) {
        savedPlaceRepository.save(
                SavedPlace.builder().member(member).place(place).build());
    }

    private CreatePlaceStateReportRequest placeStateReport(Place place) {
        return new CreatePlaceStateReportRequest(
                place.getId(), PlaceAccessStatus.PARTIALLY_ACCESSIBLE, Map.of(), List.of(), null);
    }

    private String bearer() {
        return "Bearer " + token;
    }

    @SuppressWarnings("unused")
    private Notification unused() {
        return null;
    }
}
