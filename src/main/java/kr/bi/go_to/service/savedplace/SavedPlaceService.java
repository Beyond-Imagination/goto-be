package kr.bi.go_to.service.savedplace;

import java.util.List;
import kr.bi.go_to.controller.savedplace.response.SavedPlaceResponse;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.model.savedplace.SavedPlace;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.repository.SavedPlaceRepository;
import kr.bi.go_to.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedPlaceService {

    private final SavedPlaceRepository savedPlaceRepository;
    private final PlaceRepository placeRepository;
    private final FloorMapRepository floorMapRepository;
    private final PlaceStateReportRepository placeStateReportRepository;
    private final MemberService memberService;

    public SavedPlaceService(
            SavedPlaceRepository savedPlaceRepository,
            PlaceRepository placeRepository,
            FloorMapRepository floorMapRepository,
            PlaceStateReportRepository placeStateReportRepository,
            MemberService memberService) {
        this.savedPlaceRepository = savedPlaceRepository;
        this.placeRepository = placeRepository;
        this.floorMapRepository = floorMapRepository;
        this.placeStateReportRepository = placeStateReportRepository;
        this.memberService = memberService;
    }

    /**
     * 장소를 저장한다 (하트 켜기).
     * 이미 저장한 장소를 다시 저장해도 실패하지 않는다 — 하트는 토글이라 중복 요청이 정상 흐름이다.
     */
    @Transactional
    public void save(Long memberId, Long placeId) {
        if (savedPlaceRepository.existsByMember_IdAndPlace_Id(memberId, placeId)) {
            return;
        }

        Place place =
                placeRepository.findById(placeId).orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        Member member = memberService.getUser(memberId);

        savedPlaceRepository.save(
                SavedPlace.builder().member(member).place(place).build());
    }

    /** 저장 해제 (하트 끄기). 저장하지 않은 장소를 해제해도 실패하지 않는다. */
    @Transactional
    public void unsave(Long memberId, Long placeId) {
        savedPlaceRepository.deleteByMember_IdAndPlace_Id(memberId, placeId);
    }

    /** 이 장소의 상태 변경 알림을 켜고 끈다. 저장하지 않은 장소면 404. */
    @Transactional
    public SavedPlaceResponse updateNotification(Long memberId, Long placeId, boolean enabled) {
        SavedPlace saved = savedPlaceRepository
                .findByMember_IdAndPlace_Id(memberId, placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SAVED_PLACE_NOT_FOUND));

        saved.updateNotificationEnabled(enabled);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SavedPlaceResponse> listMine(Long memberId) {
        return savedPlaceRepository.findByMember_IdOrderByCreatedAtDesc(memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 저장 목록 카드는 「최신 상태 · 마지막 확인」을 함께 보여준다(화면기획 17.2).
     * 그 값은 이 장소의 가장 최근 장소 상태 제보에서 가져온다.
     *
     * <p>TODO(GOTO-121): 저장 장소 수가 많아지면 장소별 1회 조회(N+1)가 부담이 된다.
     *  place_id IN (...) 한 번으로 최신 제보를 모아오는 쿼리로 바꿔야 한다.
     */
    private SavedPlaceResponse toResponse(SavedPlace savedPlace) {
        Long placeId = savedPlace.getPlace().getId();
        List<PlaceStateReport> latest = placeStateReportRepository.findLatestByPlace(placeId, 1);

        return SavedPlaceResponse.from(
                savedPlace, floorMapRepository.existsByPlace_Id(placeId), latest.isEmpty() ? null : latest.get(0));
    }
}
