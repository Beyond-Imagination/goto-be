package kr.bi.go_to.controller.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import kr.bi.go_to.model.member.MemberPreferences;

@Schema(name = "UpdateMySettingsRequest", description = "알림 설정과 접근성 보기 설정 수정 요청. 전달한 값으로 전체 교체된다.")
public record UpdateMySettingsRequest(
        @Schema(description = "알림 설정") @NotNull @Valid NotificationSettingsRequest notifications,
        @Schema(description = "접근성 보기 설정") @NotNull @Valid DisplaySettingsRequest display) {

    /**
     * 접근성 프로필은 이 요청의 관심사가 아니므로 기존 값을 그대로 유지한 채 설정만 교체한다.
     */
    public MemberPreferences applyTo(MemberPreferences current) {
        return new MemberPreferences(
                current.getMobilityModes(),
                current.getInformationPreferences(),
                notifications.toModel(),
                display.toModel());
    }

    @Schema(name = "NotificationSettingsRequest", description = "내 정보 06 알림 설정")
    public record NotificationSettingsRequest(
            @Schema(description = "저장한 장소의 시설 상태 변경 알림", example = "false") boolean savedPlaceStatusChange,
            @Schema(description = "저장한 장소 주변 새 장애물 제보 알림", example = "false") boolean savedPlaceNearbyObstacle,
            @Schema(description = "내 제보 확인 알림", example = "false") boolean myReportConfirmed,
            @Schema(description = "내 제보 확인 요청 알림", example = "false") boolean myReportConfirmationRequested,
            @Schema(description = "주변 도움 요청 알림", example = "false") boolean nearbyHelpRequest,
            @Schema(description = "내 도움 요청 수락 알림", example = "false") boolean myHelpRequestAccepted) {

        public MemberPreferences.NotificationSettings toModel() {
            return new MemberPreferences.NotificationSettings(
                    savedPlaceStatusChange,
                    savedPlaceNearbyObstacle,
                    myReportConfirmed,
                    myReportConfirmationRequested,
                    nearbyHelpRequest,
                    myHelpRequestAccepted);
        }
    }

    @Schema(name = "DisplaySettingsRequest", description = "내 정보 07 접근성 보기 설정")
    public record DisplaySettingsRequest(
            @Schema(description = "큰 글씨", example = "false") boolean largeText,
            @Schema(description = "고대비", example = "false") boolean highContrast,
            @Schema(description = "진동 알림", example = "false") boolean vibration,
            @Schema(description = "상태 변경 알림", example = "false") boolean statusAlerts) {

        public MemberPreferences.DisplaySettings toModel() {
            return new MemberPreferences.DisplaySettings(largeText, highContrast, vibration, statusAlerts);
        }
    }
}
