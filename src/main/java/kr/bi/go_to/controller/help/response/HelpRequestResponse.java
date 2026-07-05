package kr.bi.go_to.controller.help.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpRequest;

@Schema(name = "HelpRequestResponse", description = "도움 요청 상세 응답")
public record HelpRequestResponse(
        @Schema(description = "도움 요청 ID", example = "018f4d8d-4f30-7b4f-9f2d-7e5a96c9dd31") UUID id,
        @Schema(description = "도움 요청 상태", example = "REQUESTED") String status,
        @Schema(description = "연결된 장소 ID. 길 위 요청이면 null", nullable = true, example = "1") Long placeId,
        @Schema(description = "연결된 장소명. 길 위 요청이면 null", nullable = true, example = "국립경주박물관") String placeName,
        @Schema(description = "사용자가 입력한 현재 위치 설명", example = "국립경주박물관 앞 보도") String locationLabel,
        @Schema(description = "도움 요청 발생 위도", example = "35.8294371") BigDecimal latitude,
        @Schema(description = "도움 요청 발생 경도", example = "129.2286552") BigDecimal longitude,
        @Schema(description = "층수. 실외 요청이면 null 가능", nullable = true, example = "1") Integer floorLevel,
        @Schema(description = "요청 상세 메시지", nullable = true, example = "보도 턱 앞에서 이동 도움이 필요해요.") String message,
        @Schema(description = "요청자 닉네임", example = "requester") String requesterNickname,
        @Schema(description = "도우미 닉네임. 아직 수락 전이면 null", nullable = true, example = "helper") String helperNickname,
        @Schema(description = "요청 생성 시각") Instant requestedAt,
        @Schema(description = "요청 만료 시각") Instant expiresAt,
        @Schema(description = "도우미 수락 시각", nullable = true) Instant acceptedAt,
        @Schema(description = "도움 완료 시각", nullable = true) Instant completedAt,
        @Schema(description = "요청 취소 시각", nullable = true) Instant canceledAt,
        @Schema(description = "공유용 도움 요청 메시지", example = "현재 국립경주박물관 앞 보도 근처에서 이동 도움이 필요합니다.") String shareMessage,
        @Schema(description = "긴급 신고 권고 여부", example = "false") boolean emergencyCallRecommended) {

    public static HelpRequestResponse from(HelpRequest helpRequest) {
        return new HelpRequestResponse(
                helpRequest.getId(),
                helpRequest.getStatus().name(),
                helpRequest.getPlace() == null ? null : helpRequest.getPlace().getId(),
                helpRequest.getPlace() == null ? null : helpRequest.getPlace().getName(),
                helpRequest.getLocationLabel(),
                helpRequest.getLatitude(),
                helpRequest.getLongitude(),
                helpRequest.getFloorLevel(),
                helpRequest.getMessage(),
                helpRequest.getRequester().getNickname(),
                helpRequest.getHelper() == null ? null : helpRequest.getHelper().getNickname(),
                helpRequest.getRequestedAt(),
                helpRequest.getExpiresAt(),
                helpRequest.getAcceptedAt(),
                helpRequest.getCompletedAt(),
                helpRequest.getCanceledAt(),
                shareMessage(helpRequest),
                false);
    }

    private static String shareMessage(HelpRequest helpRequest) {
        String floor = helpRequest.getFloorLevel() == null ? "" : " " + helpRequest.getFloorLevel() + "층";
        return "현재 " + helpRequest.getLocationLabel() + floor + " 근처에서 이동 도움이 필요합니다.";
    }
}
