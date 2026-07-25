package kr.bi.go_to.controller.admin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CreateFacilityNodeRequest", description = "시설 노드 등록 요청")
public record CreateFacilityNodeRequest(
        @Schema(description = "GeoJSON feature의 node_id", example = "feature-001") String targetFeatureId,
        @Schema(description = "노드 유형 (ELEVATOR, TOILET 등)", example = "TOILET") @NotBlank String nodeType,
        @Schema(description = "노드 이름", example = "1층 장애인 화장실") String name,
        @Schema(description = "위도", example = "37.5665") @NotNull Double lat,
        @Schema(description = "경도", example = "126.9780") @NotNull Double lng,
        @Schema(description = "PDR 영점 조절 가능 여부", example = "true") Boolean isCheckpoint,
        @Schema(description = "보정 허용 반경(m)", example = "5") Integer snapRadius,
        @Schema(description = "사람이 읽을 수 있는 위치 설명", example = "신라역사관 동쪽 복도 끝, 로비에서 30m") String locationDescription) {}
