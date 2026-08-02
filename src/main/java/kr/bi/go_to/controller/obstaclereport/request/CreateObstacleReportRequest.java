package kr.bi.go_to.controller.obstaclereport.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;

@Schema(name = "CreateObstacleReportRequest", description = "장애물 제보 생성 요청")
public record CreateObstacleReportRequest(
        @Schema(description = "위도", example = "37.523850", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-90.0")
                @DecimalMax("90.0")
                Double lat,
        @Schema(description = "경도", example = "126.980470", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                Double lng,
        @Schema(description = "장애물 유형", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                ObstacleIssueType issueType,
        @Schema(description = "심각도", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ObstacleSeverity severity,
        @Schema(
                        description = "영향받는 이동조건 유형 (홈 지도 이동조건 필터에서 클러스터링에 실제로 쓰이는 값이라 필수)",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotEmpty
                Set<MobilityType> affectedMobilityTypes,
        @Schema(description = "사진 URL 목록 (이미 호스팅된 URL만 허용, 업로드 자체는 이번 스코프 밖)") List<String> photoUrls) {

    public CreateObstacleReportRequest {
        photoUrls = photoUrls == null ? List.of() : photoUrls;
    }
}
