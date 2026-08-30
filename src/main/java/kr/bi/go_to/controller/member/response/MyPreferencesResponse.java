package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import kr.bi.go_to.enums.AvoidCondition;
import kr.bi.go_to.enums.MobilityMode;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.model.member.MemberPreferences;

@Schema(name = "MyPreferencesResponse", description = "접근성 프로필 (이동 방식 · 우선 확인 시설 · 피하고 싶은 조건)")
public record MyPreferencesResponse(
        @Schema(description = "이동 방식 (복수 선택)", example = "[\"WHEELCHAIR\"]") List<MobilityMode> mobilityModes,
        @Schema(description = "우선 확인 시설 (최대 3개)", example = "[\"ELEVATOR\",\"RAMP\"]")
                List<PriorityFacility> priorityFacilities,
        @Schema(description = "피하고 싶은 조건 (최대 3개)", example = "[\"STAIRS\"]") List<AvoidCondition> avoidConditions) {

    public static MyPreferencesResponse from(MemberPreferences preferences) {
        MemberPreferences.InformationPreferences information = preferences.getInformationPreferences();
        return new MyPreferencesResponse(
                preferences.getMobilityModes(), information.getPriorityFacilities(), information.getAvoidConditions());
    }
}
