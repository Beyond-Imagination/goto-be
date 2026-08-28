package kr.bi.go_to.controller.help.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PendingHelpCountResponse", description = "대기 중인 도움 요청 건수 응답")
public record PendingHelpCountResponse(@Schema(description = "현재 대기 중인 도움 요청 건수", example = "2") long pendingCount) {}
