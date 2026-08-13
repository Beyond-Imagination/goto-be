package kr.bi.go_to.model.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import kr.bi.go_to.enums.AvoidCondition;
import kr.bi.go_to.enums.MobilityMode;
import kr.bi.go_to.enums.PriorityFacility;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberPreferences {

    private List<MobilityMode> mobilityModes = List.of();

    @JsonProperty("informationPreferences")
    private InformationPreferences informationPreferences = new InformationPreferences();

    public static MemberPreferences empty() {
        return new MemberPreferences();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InformationPreferences {

        private List<PriorityFacility> priorityFacilities = List.of();
        private List<AvoidCondition> avoidConditions = List.of();
    }
}
