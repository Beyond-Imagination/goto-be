package kr.bi.go_to.service.member;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.controller.member.request.UpdateMyPreferencesRequest;
import kr.bi.go_to.controller.member.request.UpdateMySettingsRequest;
import kr.bi.go_to.controller.member.response.MyActivityStatsResponse;
import kr.bi.go_to.controller.member.response.MyConfirmedReportResponse;
import kr.bi.go_to.controller.member.response.MyObstacleReportResponse;
import kr.bi.go_to.controller.member.response.MyPreferencesResponse;
import kr.bi.go_to.controller.member.response.MyProfileResponse;
import kr.bi.go_to.controller.member.response.MySettingsResponse;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보(마이페이지) 화면들이 쓰는 조회·수정을 담당한다.
 * 접근성 프로필과 알림·보기 설정은 모두 members.preferences JSONB 한 컬럼에 저장된다.
 */
@Service
public class MyPageService {

    private final MemberService memberService;
    private final ObstacleReportRepository obstacleReportRepository;
    private final ObstacleReportConfirmationRepository obstacleReportConfirmationRepository;
    private final Clock clock;

    public MyPageService(
            MemberService memberService,
            ObstacleReportRepository obstacleReportRepository,
            ObstacleReportConfirmationRepository obstacleReportConfirmationRepository,
            Clock clock) {
        this.memberService = memberService;
        this.obstacleReportRepository = obstacleReportRepository;
        this.obstacleReportConfirmationRepository = obstacleReportConfirmationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyProfileResponse getProfile(Long memberId) {
        Member member = memberService.getUser(memberId);
        return MyProfileResponse.of(member, getStats(memberId));
    }

    @Transactional(readOnly = true)
    public MyPreferencesResponse getPreferences(Long memberId) {
        return MyPreferencesResponse.from(memberService.getUser(memberId).getPreferences());
    }

    @Transactional
    public MyPreferencesResponse updatePreferences(Long memberId, UpdateMyPreferencesRequest request) {
        Member member = memberService.getUser(memberId);
        MemberPreferences updated = request.applyTo(member.getPreferences());
        member.updatePreferences(updated);
        return MyPreferencesResponse.from(updated);
    }

    @Transactional(readOnly = true)
    public MySettingsResponse getSettings(Long memberId) {
        return MySettingsResponse.from(memberService.getUser(memberId).getPreferences());
    }

    @Transactional
    public MySettingsResponse updateSettings(Long memberId, UpdateMySettingsRequest request) {
        Member member = memberService.getUser(memberId);
        MemberPreferences updated = request.applyTo(member.getPreferences());
        member.updatePreferences(updated);
        return MySettingsResponse.from(updated);
    }

    /**
     * TODO(GOTO-110): 실내 시설 제보(Report 엔티티)와 장소 상태 제보는 아직 조회 경로가 없어 이 목록에 포함되지 않는다.
     *  FE 내 정보 03의 분류 필터 중 「장소」·「시설」이 항상 빈 목록이 되는 원인이며,
     *  ReportRepository에 reporter 기준 조회를 추가하고 응답을 합집합으로 돌려주도록 확장이 필요하다.
     */
    @Transactional(readOnly = true)
    public List<MyObstacleReportResponse> listMyObstacleReports(Long memberId) {
        Instant now = clock.instant();
        return obstacleReportRepository.findByReporter_IdOrderByCreatedAtDesc(memberId).stream()
                .map(report -> MyObstacleReportResponse.from(report, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyConfirmedReportResponse> listMyConfirmedReports(Long memberId) {
        Instant now = clock.instant();
        return obstacleReportConfirmationRepository.findMineWithReport(memberId).stream()
                .map(confirmation -> MyConfirmedReportResponse.from(confirmation, now))
                .toList();
    }

    private MyActivityStatsResponse getStats(Long memberId) {
        return new MyActivityStatsResponse(
                obstacleReportRepository.countByReporter_Id(memberId),
                obstacleReportRepository.sumConfirmedCountByReporter(memberId),
                obstacleReportConfirmationRepository.countResolvedByMember(memberId));
    }
}
