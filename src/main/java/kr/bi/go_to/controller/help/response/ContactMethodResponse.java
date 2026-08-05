package kr.bi.go_to.controller.help.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ContactMethodResponse", description = "전화 연결이 가능한 기관 또는 장소 연락처")
public record ContactMethodResponse(
        @Schema(
                        description = "연락처 유형",
                        allowableValues = {
                            "PLACE_REPRESENTATIVE",
                            "FACILITY_MANAGER",
                            "INFORMATION_DESK",
                            "TOURIST_INFORMATION_CENTER",
                            "PUBLIC_INSTITUTION",
                            "EMERGENCY"
                        })
                String type,
        @Schema(description = "화면 표시명", example = "대표 전화") String label,
        @Schema(description = "전화번호", example = "054-740-7500") String telephone,
        @Schema(description = "연락처 데이터 출처", nullable = true, example = "KNTO") String source) {}
