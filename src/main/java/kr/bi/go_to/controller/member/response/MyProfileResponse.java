package kr.bi.go_to.controller.member.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import kr.bi.go_to.enums.MobilityMode;
import kr.bi.go_to.model.member.Member;

@Schema(name = "MyProfileResponse", description = "내 정보 홈에 표시할 프로필 요약과 활동 통계")
public record MyProfileResponse(
        @Schema(description = "회원 닉네임", example = "경주여행자") String nickname,
        @Schema(description = "선택한 이동 방식 목록", example = "[\"WHEELCHAIR\"]") List<MobilityMode> mobilityModes,
        @Schema(description = "활동 통계") MyActivityStatsResponse stats) {

    public static MyProfileResponse of(Member member, MyActivityStatsResponse stats) {
        return new MyProfileResponse(
                member.getNickname(), member.getPreferences().getMobilityModes(), stats);
    }
}
