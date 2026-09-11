package kr.bi.go_to.service.push;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpKind;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.report.FacilityIssueType;

/**
 * 푸시 문구를 한곳에 모은 곳.
 *
 * <p>알림은 잠금화면에서 한 번에 읽혀야 하므로 제목은 무슨 일이 일어났는지, 본문은 어디서·무엇이
 * 바뀌었는지를 적는다. 화면 라벨과 같은 단어를 쓰고(FE myInfoLabels·reportOptions), 색이나 이모지로만
 * 뜻을 전달하지 않는다(화면기획 설계 원칙 #5).
 */
public final class PushMessages {

    /** 알림 탭 시 열 화면. FE expo-router 경로와 같아야 한다. */
    public static final String ROUTE_SAVED_TAB = "/(tabs)/saved";

    public static final String ROUTE_OBSTACLE_DETAIL = "/report/detail";
    public static final String ROUTE_HELP_REQUEST_REVIEW = "/help/request-review";
    public static final String ROUTE_HELP_REQUEST_PENDING = "/help/request-pending";

    private static final Map<ObstacleIssueType, String> ISSUE_LABELS = Map.ofEntries(
            Map.entry(ObstacleIssueType.STAIRS, "계단"),
            Map.entry(ObstacleIssueType.HIGH_CURB, "높은 턱"),
            Map.entry(ObstacleIssueType.STEEP_SLOPE, "급경사"),
            Map.entry(ObstacleIssueType.NARROW_PASSAGE, "좁은 통로"),
            Map.entry(ObstacleIssueType.CONSTRUCTION, "공사 구간"),
            Map.entry(ObstacleIssueType.SIDEWALK_DAMAGE, "보도 파손"),
            Map.entry(ObstacleIssueType.LONG_WALKING_DISTANCE, "긴 보행 거리"),
            Map.entry(ObstacleIssueType.OBSTRUCTION, "적치물"),
            Map.entry(ObstacleIssueType.ILLEGAL_PARKING, "불법 주차"),
            Map.entry(ObstacleIssueType.BRAILLE_BLOCK_DAMAGE, "점자블록 훼손"),
            Map.entry(ObstacleIssueType.SLIPPERY_SURFACE, "미끄러운 길"),
            Map.entry(ObstacleIssueType.OTHER, "기타 장애물"));

    private static final Map<ObstacleSeverity, String> SEVERITY_LABELS = Map.of(
            ObstacleSeverity.IMPASSABLE, "통행 불가",
            ObstacleSeverity.CAUTION, "통행 주의",
            ObstacleSeverity.INFO, "참고");

    private static final Map<PlaceAccessStatus, String> ACCESS_STATUS_LABELS = Map.of(
            PlaceAccessStatus.ACCESSIBLE, "이용 편했어요",
            PlaceAccessStatus.PARTIALLY_ACCESSIBLE, "일부 불편했어요",
            PlaceAccessStatus.INACCESSIBLE, "이용 어려웠어요");

    private static final Map<HelpKind, String> HELP_KIND_LABELS = Map.of(
            HelpKind.MOBILITY_ASSIST, "이동 보조",
            HelpKind.DOOR_ASSIST, "문 열기",
            HelpKind.WAYFINDING, "길 안내",
            HelpKind.CARRY_ITEM, "짐 들기",
            HelpKind.ELEVATOR_CALL, "엘리베이터 호출",
            HelpKind.OTHER, "기타 도움");

    private static final Map<FacilityIssueType, String> FACILITY_ISSUE_LABELS = Map.of(
            FacilityIssueType.BROKEN, "고장",
            FacilityIssueType.OUT_OF_SERVICE, "운영 중지",
            FacilityIssueType.BLOCKED, "통행 막힘",
            FacilityIssueType.DAMAGED, "파손",
            FacilityIssueType.MISSING, "없어짐",
            FacilityIssueType.REPAIRED, "수리 완료",
            FacilityIssueType.OTHER, "상태 변경");

    private PushMessages() {}

    /**
     * 시설 제보의 이슈 유형 라벨.
     * reports.issue_type은 문자열이라 목록에 없는 예전 값이 올 수 있고, 그때는 "상태 변경"으로 적는다.
     */
    public static String facilityIssueLabel(String issueType) {
        try {
            return FACILITY_ISSUE_LABELS.getOrDefault(FacilityIssueType.valueOf(issueType), "상태 변경");
        } catch (IllegalArgumentException | NullPointerException exception) {
            return "상태 변경";
        }
    }

    /** 저장한 장소에 새 장소 상태 제보가 올라왔다. */
    public static PushMessage savedPlaceStateReported(Long placeId, String placeName, PlaceAccessStatus status) {
        return PushMessage.of(
                        PushNotificationType.SAVED_PLACE_STATUS_CHANGE,
                        "저장한 장소에 새 소식이 있어요",
                        "%s · %s".formatted(placeName, ACCESS_STATUS_LABELS.getOrDefault(status, "상태 제보")))
                .route(ROUTE_SAVED_TAB)
                .data("placeId", placeId)
                .build();
    }

    /** 저장한 장소의 시설(엘리베이터 등) 상태 제보가 올라왔다. */
    public static PushMessage savedPlaceFacilityReported(
            Long placeId, String placeName, String facilityName, String issueLabel) {
        String facility = facilityName == null || facilityName.isBlank() ? "시설" : facilityName;
        return PushMessage.of(
                        PushNotificationType.SAVED_PLACE_STATUS_CHANGE,
                        "저장한 장소의 시설 상태가 바뀌었어요",
                        "%s · %s %s".formatted(placeName, facility, issueLabel))
                .route(ROUTE_SAVED_TAB)
                .data("placeId", placeId)
                .build();
    }

    /** 저장한 장소 주변에 새 장애물 제보가 올라왔다. placeName은 그 회원이 저장한 가장 가까운 장소. */
    public static PushMessage savedPlaceNearbyObstacle(
            Long reportId, String placeName, ObstacleIssueType issueType, ObstacleSeverity severity) {
        String where = placeName == null || placeName.isBlank() ? "저장한 장소 근처" : placeName + " 근처";
        return PushMessage.of(
                        PushNotificationType.SAVED_PLACE_NEARBY_OBSTACLE,
                        "%s에 새 장애물 제보".formatted(where),
                        "%s · %s".formatted(label(issueType), SEVERITY_LABELS.getOrDefault(severity, "참고")))
                .route(ROUTE_OBSTACLE_DETAIL)
                .data("id", reportId)
                .build();
    }

    /** 내 제보를 누군가 "아직 있어요"로 확인했다. */
    public static PushMessage myReportConfirmed(Long reportId, ObstacleIssueType issueType, int confirmedCount) {
        return PushMessage.of(
                        PushNotificationType.MY_REPORT_CONFIRMED,
                        "내 제보가 확인됐어요",
                        "%s 제보를 %d명이 확인했어요".formatted(label(issueType), confirmedCount))
                .route(ROUTE_OBSTACLE_DETAIL)
                .data("id", reportId)
                .build();
    }

    /** 내 제보가 오래돼서 지금도 그대로인지 묻는다. */
    public static PushMessage myReportConfirmationRequested(
            Long reportId, ObstacleIssueType issueType, long daysSinceLastCheck) {
        return PushMessage.of(
                        PushNotificationType.MY_REPORT_CONFIRMATION_REQUESTED,
                        "이 제보, 지금도 그대로인가요?",
                        "%s 제보를 확인한 지 %d일이 지났어요".formatted(label(issueType), daysSinceLastCheck))
                .route(ROUTE_OBSTACLE_DETAIL)
                .data("id", reportId)
                .build();
    }

    /** 내 주변에 도움 요청이 올라왔다. */
    public static PushMessage nearbyHelpRequest(UUID helpRequestId, String locationLabel, List<HelpKind> kinds) {
        return PushMessage.of(
                        PushNotificationType.NEARBY_HELP_REQUEST,
                        "근처에서 도움이 필요해요",
                        "%s · %s".formatted(locationLabel, kindLabels(kinds)))
                .route(ROUTE_HELP_REQUEST_REVIEW)
                .data("helpRequestId", helpRequestId)
                .build();
    }

    /** 내 도움 요청을 누군가 수락했다. */
    public static PushMessage myHelpRequestAccepted(UUID helpRequestId, String helperNickname) {
        return PushMessage.of(
                        PushNotificationType.MY_HELP_REQUEST_ACCEPTED,
                        "도움 요청이 수락됐어요",
                        "%s님이 도와주러 가고 있어요".formatted(helperNickname))
                .route(ROUTE_HELP_REQUEST_PENDING)
                .data("helpRequestId", helpRequestId)
                .build();
    }

    private static String label(ObstacleIssueType issueType) {
        return ISSUE_LABELS.getOrDefault(issueType, "장애물");
    }

    /** 도움 유형은 두 개까지만 적고 나머지는 개수로 줄인다. 잠금화면에서 잘리면 뜻이 사라진다. */
    private static String kindLabels(List<HelpKind> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return "도움 요청";
        }

        List<String> labels = kinds.stream()
                .map(kind -> HELP_KIND_LABELS.getOrDefault(kind, "기타 도움"))
                .toList();
        if (labels.size() <= 2) {
            return String.join(" · ", labels);
        }
        return "%s 외 %d건".formatted(String.join(" · ", labels.subList(0, 2)), labels.size() - 2);
    }
}
