package kr.bi.go_to.controller.admin;

import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.admin.response.FloorMapResponse;
import kr.bi.go_to.model.map.FloorGeoJson;
import kr.bi.go_to.service.FloorMapService;
import kr.bi.go_to.spec.AdminFloorMapApiSpec;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/places/{placeId}/floors/{floor}")
public class AdminFloorMapController implements AdminFloorMapApiSpec {

    private final FloorMapService floorMapService;

    public AdminFloorMapController(FloorMapService floorMapService) {
        this.floorMapService = floorMapService;
    }

    @PutMapping
    @Override
    public FloorMapResponse upsertFloorMap(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long placeId,
            @PathVariable Integer floor,
            @RequestBody FloorGeoJson request) {
        return floorMapService.upsertFloorMap(member.id(), placeId, floor, request);
    }
}
