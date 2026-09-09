package kr.bi.go_to.controller.obstaclereport.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
        @Schema(description = "사진 URL 목록 (업로드 API로 올린 뒤 받은 URL만 허용)") List<String> photoUrls,
        @Schema(description = "메모 (선택). 유형·심각도로 표현되지 않는 상황 설명", example = "보도가 깨져서 휠체어가 지나가기 어려워요") @Size(max = 1000)
                String description) {

    public CreateObstacleReportRequest {
        photoUrls = photoUrls == null ? List.of() : photoUrls;
        // 빈 문자열은 "메모 없음"과 같게 다룬다.
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
