package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameAvailabilityResponse(@Schema(description = "사용 가능 여부", example = "true") boolean available) {}
