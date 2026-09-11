package kr.bi.go_to.service.placereport;

import java.util.EnumMap;
import java.util.List;
import kr.bi.go_to.controller.placereport.request.CreatePlaceStateReportRequest;
import kr.bi.go_to.controller.placereport.response.PlaceStateReportResponse;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.placereport.PlaceFacilityStatus;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.service.MemberService;
import kr.bi.go_to.service.push.event.PlaceStateReportedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 단위 상태 제보(제보 03·04 화면)를 담당한다.
 * 장애물 제보와 달리 좌표를 받지 않고 장소 ID에 붙으며, place_bf_info를 덮어쓰지 않는다.
 */
@Service
public class PlaceStateReportService {

    /** 장소 상세에서 함께 보여줄 최근 제보 수. */
    private static final int DEFAULT_PLACE_REPORT_LIMIT = 20;

    private final PlaceStateReportRepository placeStateReportRepository;
    private final PlaceRepository placeRepository;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    public PlaceStateReportService(
            PlaceStateReportRepository placeStateReportRepository,
            PlaceRepository placeRepository,
            MemberService memberService,
            ApplicationEventPublisher eventPublisher) {
        this.placeStateReportRepository = placeStateReportRepository;
        this.placeRepository = placeRepository;
        this.memberService = memberService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PlaceStateReportResponse create(Long memberId, CreatePlaceStateReportRequest request) {
        Member reporter = memberService.getUser(memberId);
        Place place = placeRepository
                .findById(request.placeId())
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        PlaceStateReport report = PlaceStateReport.builder()
                .place(place)
                .reporter(reporter)
                .accessStatus(request.accessStatus())
                .facilityStatuses(toFacilityStatuses(request))
                .photoUrls(request.photoUrls())
                .description(request.description())
                .build();

        PlaceStateReport saved = placeStateReportRepository.save(report);
        // 이 장소를 저장해 둔 사람들에게 알린다. 커밋 이후에 발송된다(PushNotificationEventListener).
        eventPublisher.publishEvent(
                new PlaceStateReportedEvent(place.getId(), place.getName(), memberId, saved.getAccessStatus()));

        return PlaceStateReportResponse.from(saved);
    }

    /** EnumMap은 빈 Map을 그대로 받으면 키 타입을 알 수 없어 생성자에서 예외가 난다. */
    private static EnumMap<PriorityFacility, PlaceFacilityStatus> toFacilityStatuses(
            CreatePlaceStateReportRequest request) {
        EnumMap<PriorityFacility, PlaceFacilityStatus> statuses = new EnumMap<>(PriorityFacility.class);
        statuses.putAll(request.facilityStatuses());
        return statuses;
    }

    @Transactional(readOnly = true)
    public PlaceStateReportResponse get(Long reportId) {
        return PlaceStateReportResponse.from(placeStateReportRepository
                .findByIdWithPlace(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_STATE_REPORT_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public List<PlaceStateReportResponse> listByPlace(Long placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }

        return placeStateReportRepository.findLatestByPlace(placeId, DEFAULT_PLACE_REPORT_LIMIT).stream()
                .map(PlaceStateReportResponse::from)
                .toList();
    }
}
