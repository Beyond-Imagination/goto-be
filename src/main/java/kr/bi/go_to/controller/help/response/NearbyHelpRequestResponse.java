package kr.bi.go_to.controller.help.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpRequest;

@Schema(name = "NearbyHelpRequestResponse", description = "주변 도움 요청 목록 응답")
public record NearbyHelpRequestResponse(
        @Schema(description = "도움 요청 ID", example = "018f4d8d-4f30-7b4f-9f2d-7e5a96c9dd31") UUID id,
        @Schema(description = "연결된 장소 ID. 길 위 요청이면 null", nullable = true, example = "1") Long placeId,
        @Schema(description = "연결된 장소명. 길 위 요청이면 null", nullable = true, example = "국립경주박물관") String placeName,
        @Schema(description = "사용자가 입력한 현재 위치 설명", example = "국립경주박물관 앞 보도") String locationLabel,
        @Schema(description = "요청 상세 메시지", nullable = true, example = "보도 턱 앞에서 이동 도움이 필요해요.") String message,
        @Schema(description = "조회 위치와 요청 위치 사이 거리(미터)", example = "42") long distanceMeters,
        @Schema(description = "요청 생성 시각") Instant requestedAt,
        @Schema(description = "요청 만료 시각") Instant expiresAt) {

    public static NearbyHelpRequestResponse of(HelpRequest helpRequest, long distanceMeters) {
        return new NearbyHelpRequestResponse(
                helpRequest.getId(),
                helpRequest.getPlace() == null ? null : helpRequest.getPlace().getId(),
                helpRequest.getPlace() == null ? null : helpRequest.getPlace().getName(),
                helpRequest.getLocationLabel(),
                helpRequest.getMessage(),
                distanceMeters,
                helpRequest.getRequestedAt(),
                helpRequest.getExpiresAt());
    }
}
