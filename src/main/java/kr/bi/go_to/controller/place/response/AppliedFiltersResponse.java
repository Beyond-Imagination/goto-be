package kr.bi.go_to.controller.place.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;

/**
 * {@code filters}(선택 가능한 전체 옵션 목록)와는 별개로, 이번 요청에 실제로 사용된 필터 값을 echo한다.
 * 프론트가 필터 칩 상태를 복원하는 용도.
 */
@Schema(name = "AppliedFiltersResponse", description = "이번 탐색 요청에 실제로 적용된 필터 값")
public record AppliedFiltersResponse(
        @Schema(description = "적용된 장소유형 prefix 목록") Set<String> categoryPrefixes,
        @Schema(description = "적용된 이동조건 목록") Set<MobilityType> mobilityTypes,
        @Schema(description = "적용된 회피구간 목록") Set<ObstacleIssueType> avoid) {
    public AppliedFiltersResponse {
        categoryPrefixes = categoryPrefixes == null ? Set.of() : Set.copyOf(categoryPrefixes);
        mobilityTypes = mobilityTypes == null ? Set.of() : Set.copyOf(mobilityTypes);
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
    }
}
