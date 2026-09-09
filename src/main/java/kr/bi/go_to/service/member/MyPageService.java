package kr.bi.go_to.service.member;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.controller.member.request.UpdateMyPreferencesRequest;
import kr.bi.go_to.controller.member.request.UpdateMySettingsRequest;
import kr.bi.go_to.controller.member.response.MyActivityStatsResponse;
import kr.bi.go_to.controller.member.response.MyConfirmedReportResponse;
import kr.bi.go_to.controller.member.response.MyFacilityReportResponse;
import kr.bi.go_to.controller.member.response.MyObstacleReportResponse;
import kr.bi.go_to.controller.member.response.MyPlaceStateReportResponse;
import kr.bi.go_to.controller.member.response.MyPreferencesResponse;
import kr.bi.go_to.controller.member.response.MyProfileResponse;
import kr.bi.go_to.controller.member.response.MySettingsResponse;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.repository.ObstacleReportConfirmationRepository;
import kr.bi.go_to.repository.ObstacleReportRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.repository.ReportRepository;
import kr.bi.go_to.service.MemberService;
import kr.bi.go_to.service.obstaclereport.geocoding.NaverReverseGeocodingClient;
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
    private final PlaceStateReportRepository placeStateReportRepository;
    private final ReportRepository reportRepository;
    private final NaverReverseGeocodingClient naverReverseGeocodingClient;
    private final Clock clock;

    public MyPageService(
            MemberService memberService,
            ObstacleReportRepository obstacleReportRepository,
            ObstacleReportConfirmationRepository obstacleReportConfirmationRepository,
            PlaceStateReportRepository placeStateReportRepository,
            ReportRepository reportRepository,
            NaverReverseGeocodingClient naverReverseGeocodingClient,
            Clock clock) {
        this.memberService = memberService;
        this.obstacleReportRepository = obstacleReportRepository;
        this.obstacleReportConfirmationRepository = obstacleReportConfirmationRepository;
        this.placeStateReportRepository = placeStateReportRepository;
        this.reportRepository = reportRepository;
        this.naverReverseGeocodingClient = naverReverseGeocodingClient;
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
     * 장애물 제보만 반환한다. 장소·시설 제보는 필요한 필드가 달라
     * listMyPlaceStateReports·listMyFacilityReports로 각각 분리했다.
     */
    @Transactional(readOnly = true)
    public List<MyObstacleReportResponse> listMyObstacleReports(Long memberId) {
        Instant now = clock.instant();
        return obstacleReportRepository.findByReporter_IdOrderByCreatedAtDesc(memberId).stream()
                .map(report -> MyObstacleReportResponse.from(report, now, resolveAddress(report)))
                .toList();
    }

    /** 내 제보 기록의 「장소」 분류. 목록이 장소명·주소를 쓰므로 Place를 fetch join 해서 가져온다. */
    @Transactional(readOnly = true)
    public List<MyPlaceStateReportResponse> listMyPlaceStateReports(Long memberId) {
        return placeStateReportRepository.findMineWithPlace(memberId).stream()
                .map(MyPlaceStateReportResponse::from)
                .toList();
    }

    /** 내 제보 기록의 「시설」 분류. 목록이 시설명·층·장소명을 쓰므로 노드·층·장소를 fetch join 해서 가져온다. */
    @Transactional(readOnly = true)
    public List<MyFacilityReportResponse> listMyFacilityReports(Long memberId) {
        return reportRepository.findMineWithNodeAndPlace(memberId).stream()
                .map(MyFacilityReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyConfirmedReportResponse> listMyConfirmedReports(Long memberId) {
        Instant now = clock.instant();
        return obstacleReportConfirmationRepository.findMineWithReport(memberId).stream()
                .map(confirmation -> MyConfirmedReportResponse.from(
                        confirmation, now, resolveAddress(confirmation.getObstacleReport())))
                .toList();
    }

    /**
     * 제보 좌표를 행정동 이름으로 바꾼다.
     * 키 미설정·호출 실패·매칭 없음은 모두 null이 되고, 그 경우 FE가 좌표 표기로 대체한다.
     * 좌표를 ~100m 격자로 반올림한 키로 캐싱되므로 목록에 인접한 제보가 여러 건 있어도 외부 호출은 한 번이다.
     */
    private String resolveAddress(ObstacleReport report) {
        return naverReverseGeocodingClient
                .reverseGeocode(
                        report.getLocationPoint().getY(),
                        report.getLocationPoint().getX())
                .orElse(null);
    }

    private MyActivityStatsResponse getStats(Long memberId) {
        // 「제보건수」는 사용자가 남긴 제보 전체다. 장애물·장소·시설 제보를 함께 센다.
        return new MyActivityStatsResponse(
                obstacleReportRepository.countByReporter_Id(memberId)
                        + placeStateReportRepository.countByReporter_Id(memberId)
                        + reportRepository.countByReporter_Id(memberId),
                obstacleReportRepository.sumConfirmedCountByReporter(memberId),
                obstacleReportConfirmationRepository.countResolvedByMember(memberId));
    }
}
