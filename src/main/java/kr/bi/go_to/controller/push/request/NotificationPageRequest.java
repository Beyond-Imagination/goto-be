package kr.bi.go_to.controller.push.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(name = "NotificationPageRequest", description = "알림 목록 조회 조건")
public record NotificationPageRequest(
        @Schema(description = "이전 응답의 nextCursor. 첫 페이지는 생략합니다", nullable = true) String cursor,
        @Schema(description = "한 번에 가져올 개수", example = "20") @Min(1) @Max(50) Integer size) {

    private static final int DEFAULT_SIZE = 20;

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
