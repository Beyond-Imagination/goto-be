package kr.bi.go_to.service.member;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.bi.go_to.controller.member.request.MyConfirmedReportPageRequest;
import kr.bi.go_to.controller.member.request.MyReportPageRequest;
import kr.bi.go_to.controller.member.request.UpdateMyPreferencesRequest;
import kr.bi.go_to.controller.member.request.UpdateMySettingsRequest;
import kr.bi.go_to.controller.member.response.MyActivityStatsResponse;
import kr.bi.go_to.controller.member.response.MyConfirmedReportPageResponse;
import kr.bi.go_to.controller.member.response.MyConfirmedReportResponse;
import kr.bi.go_to.controller.member.response.MyFacilityReportResponse;
import kr.bi.go_to.controller.member.response.MyObstacleReportResponse;
import kr.bi.go_to.controller.member.response.MyPlaceStateReportResponse;
import kr.bi.go_to.controller.member.response.MyPreferencesResponse;
import kr.bi.go_to.controller.member.response.MyProfileResponse;
import kr.bi.go_to.controller.member.response.MyReportItemResponse;
import kr.bi.go_to.controller.member.response.MyReportPageResponse;
import kr.bi.go_to.controller.member.response.MySettingsResponse;
import kr.bi.go_to.enums.MyReportKind;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.member.MemberPreferences;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;
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
     * 내가 작성한 장애물 제보 전체. 「지도로 보기」(MyReportsMapScreen)가 핀을 한 번에 찍어야 해서
     * 페이지네이션 없이 그대로 내려준다. 목록 화면은 listMyReports(커서 페이지)를 쓴다.
     */
    @Transactional(readOnly = true)
    public List<MyObstacleReportResponse> listMyObstacleReports(Long memberId) {
        Instant now = clock.instant();
        return obstacleReportRepository.findByReporter_IdOrderByCreatedAtDesc(memberId).stream()
                .map(report -> MyObstacleReportResponse.from(report, now, resolveAddress(report)))
                .toList();
    }

    /**
     * 내 제보 기록(내 정보 03) 한 페이지.
     *
     * <p>장애물·장소·시설은 테이블이 달라 id를 서로 비교할 수 없다. 그래서 분류마다 한 페이지씩
     * 넉넉히(size + 1) 읽어 최신순으로 합치고, 실제로 내보낸 마지막 항목의 위치만 분류별로 커서에
     * 담는다. 내보내지 못한 항목은 커서가 그대로 남아 다음 페이지에서 다시 읽히므로 누락이 없다.
     */
    @Transactional(readOnly = true)
    public MyReportPageResponse listMyReports(Long memberId, MyReportPageRequest request) {
        int size = request.sizeOrDefault();
        MyReportKind kindFilter = request.kind();
        ReportCursor cursor = ReportCursor.decode(request.cursor());
        Instant now = clock.instant();

        List<MyReportItemResponse> candidates = new ArrayList<>();
        if (kindFilter == null || kindFilter == MyReportKind.OBSTACLE) {
            ReportCursor.Position position = cursor.get(MyReportCursorKey.OBSTACLE);
            obstacleReportRepository
                    .findMinePage(memberId, createdAtOf(position), idOf(position), size + 1)
                    .forEach(report -> candidates.add(MyReportItemResponse.ofObstacle(
                            MyObstacleReportResponse.from(report, now, resolveAddress(report)))));
        }
        if (kindFilter == null || kindFilter == MyReportKind.PLACE) {
            ReportCursor.Position position = cursor.get(MyReportCursorKey.PLACE);
            placeStateReportRepository
                    .findMinePage(memberId, createdAtOf(position), idOf(position), size + 1)
                    .forEach(report ->
                            candidates.add(MyReportItemResponse.ofPlace(MyPlaceStateReportResponse.from(report))));
        }
        if (kindFilter == null || kindFilter == MyReportKind.FACILITY) {
            ReportCursor.Position position = cursor.get(MyReportCursorKey.FACILITY);
            reportRepository
                    .findMinePage(memberId, createdAtOf(position), idOf(position), size + 1)
                    .forEach(report ->
                            candidates.add(MyReportItemResponse.ofFacility(MyFacilityReportResponse.from(report))));
        }

        // 같은 시각이면 분류·id 순으로 갈라 페이지 경계가 요청마다 흔들리지 않게 한다.
        candidates.sort(Comparator.comparing(MyReportItemResponse::createdAt)
                .thenComparing(MyReportItemResponse::id)
                .reversed());

        boolean hasNext = candidates.size() > size;
        List<MyReportItemResponse> items = hasNext ? List.copyOf(candidates.subList(0, size)) : List.copyOf(candidates);

        return new MyReportPageResponse(
                items, hasNext ? nextCursor(cursor, items).encode() : null);
    }

    /** 내가 확인한 리포트(내 정보 05) 한 페이지. 확인 시각 기준 최신순이다. */
    @Transactional(readOnly = true)
    public MyConfirmedReportPageResponse listMyConfirmedReports(Long memberId, MyConfirmedReportPageRequest request) {
        int size = request.sizeOrDefault();
        ReportCursor cursor = ReportCursor.decode(request.cursor());
        ReportCursor.Position position = cursor.get(MyReportCursorKey.CONFIRMATION);
        Instant now = clock.instant();

        List<ObstacleReportConfirmation> confirmations = obstacleReportConfirmationRepository.findMinePage(
                memberId, request.status(), createdAtOf(position), idOf(position), size + 1);

        boolean hasNext = confirmations.size() > size;
        List<ObstacleReportConfirmation> pageRows = hasNext ? confirmations.subList(0, size) : confirmations;
        List<MyConfirmedReportResponse> items = pageRows.stream()
                .map(confirmation -> MyConfirmedReportResponse.from(
                        confirmation, now, resolveAddress(confirmation.getObstacleReport())))
                .toList();

        if (!hasNext) {
            return new MyConfirmedReportPageResponse(items, null);
        }

        ObstacleReportConfirmation last = pageRows.get(pageRows.size() - 1);
        String encoded = cursor.with(
                        MyReportCursorKey.CONFIRMATION, new ReportCursor.Position(last.getCreatedAt(), last.getId()))
                .encode();
        return new MyConfirmedReportPageResponse(items, encoded);
    }

    /** 내보낸 항목이 있는 분류만 위치를 갱신하고, 없는 분류는 이전 위치를 그대로 남긴다. */
    private ReportCursor nextCursor(ReportCursor previous, List<MyReportItemResponse> items) {
        ReportCursor next = previous;
        for (MyReportKind kind : MyReportKind.values()) {
            MyReportItemResponse last = null;
            for (MyReportItemResponse item : items) {
                if (item.kind() == kind) {
                    last = item;
                }
            }
            if (last != null) {
                next = next.with(
                        MyReportCursorKey.valueOf(kind.name()), new ReportCursor.Position(last.createdAt(), last.id()));
            }
        }
        return next;
    }

    private static Instant createdAtOf(ReportCursor.Position position) {
        return position == null ? null : position.createdAt();
    }

    private static Long idOf(ReportCursor.Position position) {
        return position == null ? null : position.id();
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
