package kr.bi.go_to.controller.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "임시 로그인 요청")
public record LoginRequest(
        @Schema(description = "사용자 닉네임", example = "demo") @NotBlank String nickname,
        @Schema(description = "사용자 비밀번호", example = "demo") @NotBlank String password) {}
