package kr.bi.go_to.service.savedplace;

import java.util.List;
import kr.bi.go_to.controller.savedplace.response.SavedPlaceResponse;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.savedplace.SavedPlace;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.SavedPlaceRepository;
import kr.bi.go_to.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedPlaceService {

    private final SavedPlaceRepository savedPlaceRepository;
    private final PlaceRepository placeRepository;
    private final FloorMapRepository floorMapRepository;
    private final MemberService memberService;

    public SavedPlaceService(
            SavedPlaceRepository savedPlaceRepository,
            PlaceRepository placeRepository,
            FloorMapRepository floorMapRepository,
            MemberService memberService) {
        this.savedPlaceRepository = savedPlaceRepository;
        this.placeRepository = placeRepository;
        this.floorMapRepository = floorMapRepository;
        this.memberService = memberService;
    }

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

    @Transactional
    public void unsave(Long memberId, Long placeId) {
        savedPlaceRepository.deleteByMember_IdAndPlace_Id(memberId, placeId);
    }

    @Transactional(readOnly = true)
    public List<SavedPlaceResponse> listMine(Long memberId) {
        return savedPlaceRepository.findByMember_IdOrderByCreatedAtDesc(memberId).stream()
                .map(savedPlace -> SavedPlaceResponse.from(
                        savedPlace,
                        floorMapRepository.existsByPlace_Id(
                                savedPlace.getPlace().getId())))
                .toList();
    }
}
