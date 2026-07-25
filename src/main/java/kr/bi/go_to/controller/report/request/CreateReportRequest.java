package kr.bi.go_to.controller.report.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateReportRequest", description = "시설물 상태 제보 생성 요청")
public record CreateReportRequest(
        @Schema(description = "제보할 시설물 노드 ID", example = "1") @NotNull @Positive Long nodeId,
        @Schema(description = "제보 이슈 유형", example = "BROKEN") @NotBlank @Size(max = 50) String issueType,
        @Schema(description = "제보 상세 내용", nullable = true, example = "엘리베이터가 멈춰 있어요.") @Size(max = 1000)
                String description) {}
