package kr.bi.go_to.service;

import java.util.List;
import kr.bi.go_to.controller.admin.response.FloorMapResponse;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.map.FloorGeoJson;
import kr.bi.go_to.model.map.FloorMap;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.FloorMapRepository;
import kr.bi.go_to.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FloorMapService {

    private final FloorMapRepository floorMapRepository;
    private final PlaceRepository placeRepository;
    private final MemberService memberService;

    public FloorMapService(
            FloorMapRepository floorMapRepository, PlaceRepository placeRepository, MemberService memberService) {
        this.floorMapRepository = floorMapRepository;
        this.placeRepository = placeRepository;
        this.memberService = memberService;
    }

    @Transactional
    public FloorMapResponse upsertFloorMap(Long memberId, Long placeId, Integer floor, FloorGeoJson geojsonData) {
        FloorMap floorMap = floorMapRepository
                .findByPlace_IdAndFloorLevel(placeId, floor)
                .map(existing -> {
                    existing.replaceGeojsonData(geojsonData);
                    return existing;
                })
                .orElseGet(() -> {
                    Place place = placeRepository
                            .findById(placeId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
                    Member createdBy = memberService.getUser(memberId);
                    return floorMapRepository.save(FloorMap.builder()
                            .place(place)
                            .floorLevel(floor)
                            .geojsonData(geojsonData)
                            .createdBy(createdBy)
                            .build());
                });

        return FloorMapResponse.from(floorMap);
    }

    @Transactional(readOnly = true)
    public FloorGeoJson getIndoorMap(Long placeId, Integer floor) {
        return floorMapRepository
                .findByPlace_IdAndFloorLevel(placeId, floor)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLOOR_MAP_NOT_FOUND))
                .getGeojsonData();
    }

    @Transactional(readOnly = true)
    public List<Integer> listFloorLevels(Long placeId) {
        return floorMapRepository.findFloorLevelByPlace_IdOrderByFloorLevelAsc(placeId);
    }
}
