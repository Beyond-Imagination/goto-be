package kr.bi.go_to.controller.member.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import kr.bi.go_to.enums.AvoidCondition;
import kr.bi.go_to.enums.MobilityMode;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.model.member.MemberPreferences;

@Schema(name = "UpdateMyPreferencesRequest", description = "접근성 프로필 수정 요청. 전달한 값으로 전체 교체된다.")
public record UpdateMyPreferencesRequest(
        @Schema(description = "이동 방식 (복수 선택, 빈 배열 허용)", example = "[\"WHEELCHAIR\"]") @NotNull
                List<MobilityMode> mobilityModes,
        @Schema(description = "우선 확인 시설 (최대 3개)", example = "[\"ELEVATOR\",\"RAMP\"]")
                @NotNull
                @Size(max = MAX_SELECTION)
                List<PriorityFacility> priorityFacilities,
        @Schema(description = "피하고 싶은 조건 (최대 3개)", example = "[\"STAIRS\"]") @NotNull @Size(max = MAX_SELECTION)
                List<AvoidCondition> avoidConditions) {

    /**
     * 화면기획 7.2 — 우선 확인 시설과 피하고 싶은 조건은 각각 최대 3개까지 선택한다.
     */
    public static final int MAX_SELECTION = 3;

    /**
     * 알림·보기 설정은 이 요청의 관심사가 아니므로 기존 값을 그대로 유지한 채 접근성 프로필만 교체한다.
     */
    public MemberPreferences applyTo(MemberPreferences current) {
        return new MemberPreferences(
                List.copyOf(mobilityModes),
                new MemberPreferences.InformationPreferences(
                        List.copyOf(priorityFacilities), List.copyOf(avoidConditions)),
                current.getNotificationSettings(),
                current.getDisplaySettings());
    }
}
