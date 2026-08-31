package kr.bi.go_to.controller.member;

import jakarta.validation.Valid;
import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.member.request.UpdateMyPreferencesRequest;
import kr.bi.go_to.controller.member.request.UpdateMySettingsRequest;
import kr.bi.go_to.controller.member.response.MyConfirmedReportResponse;
import kr.bi.go_to.controller.member.response.MyObstacleReportResponse;
import kr.bi.go_to.controller.member.response.MyPreferencesResponse;
import kr.bi.go_to.controller.member.response.MyProfileResponse;
import kr.bi.go_to.controller.member.response.MySettingsResponse;
import kr.bi.go_to.service.member.MyPageService;
import kr.bi.go_to.spec.MyPageApiSpec;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보(마이페이지) API.
 * 닉네임 중복확인은 permitAll 경로라 MemberController에 그대로 두고, 인증이 필요한 마이페이지는 이 컨트롤러로 분리한다.
 */
@RestController
@RequestMapping("/api/v1/members/me")
public class MyPageController implements MyPageApiSpec {

    private final MyPageService myPageService;

    public MyPageController(MyPageService myPageService) {
        this.myPageService = myPageService;
    }

    @Override
    @GetMapping
    public MyProfileResponse getProfile(@AuthenticationPrincipal AuthenticatedMember member) {
        return myPageService.getProfile(member.id());
    }

    @Override
    @GetMapping("/preferences")
    public MyPreferencesResponse getPreferences(@AuthenticationPrincipal AuthenticatedMember member) {
        return myPageService.getPreferences(member.id());
    }

    @Override
    @PutMapping("/preferences")
    public MyPreferencesResponse updatePreferences(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody UpdateMyPreferencesRequest request) {
        return myPageService.updatePreferences(member.id(), request);
    }

    @Override
    @GetMapping("/settings")
    public MySettingsResponse getSettings(@AuthenticationPrincipal AuthenticatedMember member) {
        return myPageService.getSettings(member.id());
    }

    @Override
    @PutMapping("/settings")
    public MySettingsResponse updateSettings(
            @AuthenticationPrincipal AuthenticatedMember member, @Valid @RequestBody UpdateMySettingsRequest request) {
        return myPageService.updateSettings(member.id(), request);
    }

    @Override
    @GetMapping("/obstacle-reports")
    public List<MyObstacleReportResponse> findMyObstacleReports(@AuthenticationPrincipal AuthenticatedMember member) {
        return myPageService.listMyObstacleReports(member.id());
    }

    @Override
    @GetMapping("/obstacle-report-confirmations")
    public List<MyConfirmedReportResponse> findMyConfirmedReports(@AuthenticationPrincipal AuthenticatedMember member) {
        return myPageService.listMyConfirmedReports(member.id());
    }
}
