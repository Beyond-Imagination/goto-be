package kr.bi.go_to.controller.admin;

import jakarta.validation.Valid;
import kr.bi.go_to.controller.admin.request.CreateFacilityNodeRequest;
import kr.bi.go_to.controller.admin.response.FacilityNodeResponse;
import kr.bi.go_to.service.FacilityNodeService;
import kr.bi.go_to.spec.AdminFacilityNodeApiSpec;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/places/{placeId}/floors/{floor}/nodes")
public class AdminFacilityNodeController implements AdminFacilityNodeApiSpec {

    private final FacilityNodeService facilityNodeService;

    public AdminFacilityNodeController(FacilityNodeService facilityNodeService) {
        this.facilityNodeService = facilityNodeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public FacilityNodeResponse createNode(
            @PathVariable Long placeId,
            @PathVariable Integer floor,
            @Valid @RequestBody CreateFacilityNodeRequest request) {
        return facilityNodeService.createNode(placeId, floor, request);
    }
}
