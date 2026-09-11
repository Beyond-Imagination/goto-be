package kr.bi.go_to.controller.push.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UnreadNotificationCountResponse", description = "안 읽은 알림 수")
public record UnreadNotificationCountResponse(@Schema(description = "안 읽은 알림 수", example = "3") long count) {}
