package kr.bi.go_to.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.admin.request.CreateFacilityNodeRequest;
import kr.bi.go_to.controller.admin.response.FacilityNodeResponse;
import kr.bi.go_to.enums.SwaggerTag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = SwaggerTag.ADMIN_NAME, description = SwaggerTag.ADMIN_DESCRIPTION)
public interface AdminFacilityNodeApiSpec {

    @Operation(
            tags = SwaggerTag.ADMIN_NAME,
            summary = "시설 노드 등록",
            description = "특정 장소의 층 도면에 시설 노드(엘리베이터, 화장실 등)를 등록합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "노드 등록 성공",
                content = @Content(schema = @Schema(implementation = FacilityNodeResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패 또는 targetFeatureId가 도면 GeoJSON에 존재하지 않음",
                content = @Content),
        @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content),
        @ApiResponse(responseCode = "404", description = "층 도면 없음", content = @Content)
    })
    FacilityNodeResponse createNode(
            AuthenticatedMember member,
            @PathVariable Long placeId,
            @PathVariable Integer floor,
            @Valid @RequestBody CreateFacilityNodeRequest request);
}
