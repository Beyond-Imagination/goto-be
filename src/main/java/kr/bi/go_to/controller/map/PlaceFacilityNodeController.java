package kr.bi.go_to.controller.map;

import java.util.List;
import kr.bi.go_to.controller.admin.response.FacilityNodeResponse;
import kr.bi.go_to.service.FacilityNodeService;
import kr.bi.go_to.spec.PlaceFacilityNodeApiSpec;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places/{placeId}/floors/{floor}")
public class PlaceFacilityNodeController implements PlaceFacilityNodeApiSpec {

    private final FacilityNodeService facilityNodeService;

    public PlaceFacilityNodeController(FacilityNodeService facilityNodeService) {
        this.facilityNodeService = facilityNodeService;
    }

    @GetMapping("/nodes")
    @Override
    public List<FacilityNodeResponse> listNodes(@PathVariable Long placeId, @PathVariable Integer floor) {
        return facilityNodeService.listNodes(placeId, floor);
    }
}
