package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.bi.go_to.model.member.MemberPreferences;

@Schema(name = "MySettingsResponse", description = "알림 설정과 접근성 보기 설정")
public record MySettingsResponse(
        @Schema(description = "알림 설정") NotificationSettingsResponse notifications,
        @Schema(description = "접근성 보기 설정") DisplaySettingsResponse display) {

    public static MySettingsResponse from(MemberPreferences preferences) {
        return new MySettingsResponse(
                NotificationSettingsResponse.from(preferences.getNotificationSettings()),
                DisplaySettingsResponse.from(preferences.getDisplaySettings()));
    }

    @Schema(name = "NotificationSettingsResponse", description = "내 정보 06 알림 설정")
    public record NotificationSettingsResponse(
            @Schema(description = "저장한 장소의 시설 상태 변경 알림", example = "false") boolean savedPlaceStatusChange,
            @Schema(description = "저장한 장소 주변 새 장애물 제보 알림", example = "false") boolean savedPlaceNearbyObstacle,
            @Schema(description = "내 제보 확인 알림", example = "false") boolean myReportConfirmed,
            @Schema(description = "내 제보 확인 요청 알림", example = "false") boolean myReportConfirmationRequested,
            @Schema(description = "주변 도움 요청 알림", example = "false") boolean nearbyHelpRequest,
            @Schema(description = "내 도움 요청 수락 알림", example = "false") boolean myHelpRequestAccepted) {

        public static NotificationSettingsResponse from(MemberPreferences.NotificationSettings settings) {
            return new NotificationSettingsResponse(
                    settings.isSavedPlaceStatusChange(),
                    settings.isSavedPlaceNearbyObstacle(),
                    settings.isMyReportConfirmed(),
                    settings.isMyReportConfirmationRequested(),
                    settings.isNearbyHelpRequest(),
                    settings.isMyHelpRequestAccepted());
        }
    }

    @Schema(name = "DisplaySettingsResponse", description = "내 정보 07 접근성 보기 설정")
    public record DisplaySettingsResponse(
            @Schema(description = "큰 글씨", example = "false") boolean largeText,
            @Schema(description = "고대비", example = "false") boolean highContrast,
            @Schema(description = "진동 알림", example = "false") boolean vibration,
            @Schema(description = "상태 변경 알림", example = "false") boolean statusAlerts) {

        public static DisplaySettingsResponse from(MemberPreferences.DisplaySettings settings) {
            return new DisplaySettingsResponse(
                    settings.isLargeText(),
                    settings.isHighContrast(),
                    settings.isVibration(),
                    settings.isStatusAlerts());
        }
    }
}
