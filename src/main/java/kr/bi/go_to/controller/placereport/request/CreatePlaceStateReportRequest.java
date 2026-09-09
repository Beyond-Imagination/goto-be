package kr.bi.go_to.controller.placereport.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.placereport.PlaceFacilityStatus;

@Schema(name = "CreatePlaceStateReportRequest", description = "장소 상태 제보 생성 요청")
public record CreatePlaceStateReportRequest(
        @Schema(description = "제보 대상 장소 ID", example = "1247", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long placeId,
        @Schema(description = "장소 전반의 이용 난이도", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                PlaceAccessStatus accessStatus,
        @Schema(
                        description = "편의시설별 상태 (선택). 확인하지 못한 항목은 키를 보내지 않는다",
                        example = "{\"ELEVATOR\":\"AVAILABLE\",\"ACCESSIBLE_TOILET\":\"BROKEN\"}")
                Map<PriorityFacility, PlaceFacilityStatus> facilityStatuses,
        @Schema(description = "사진 URL 목록 (업로드 API로 올린 뒤 받은 URL만 허용)") List<String> photoUrls,
        @Schema(description = "메모 (선택)", example = "정문 경사로는 있지만 문이 무거워 혼자 열기 어려워요") @Size(max = 1000)
                String description) {

    public CreatePlaceStateReportRequest {
        facilityStatuses = facilityStatuses == null ? Map.of() : facilityStatuses;
        photoUrls = photoUrls == null ? List.of() : photoUrls;
        // 빈 문자열은 "메모 없음"과 같게 다룬다.
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
