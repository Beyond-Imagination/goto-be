package kr.bi.go_to.controller.placereport;

import jakarta.validation.Valid;
import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.placereport.request.CreatePlaceStateReportRequest;
import kr.bi.go_to.controller.placereport.response.PlaceStateReportResponse;
import kr.bi.go_to.service.placereport.PlaceStateReportService;
import kr.bi.go_to.spec.PlaceStateReportApiSpec;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 단위 상태 제보 API.
 * 장소별 목록은 장소 상세 화면이 쓰므로 /api/v1/places/{placeId}/state-reports로 함께 노출한다.
 */
@RestController
@RequestMapping("/api/v1")
public class PlaceStateReportController implements PlaceStateReportApiSpec {

    private final PlaceStateReportService placeStateReportService;

    public PlaceStateReportController(PlaceStateReportService placeStateReportService) {
        this.placeStateReportService = placeStateReportService;
    }

    @Override
    @PostMapping("/place-state-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceStateReportResponse create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody CreatePlaceStateReportRequest request) {
        return placeStateReportService.create(member.id(), request);
    }

    @Override
    @GetMapping("/place-state-reports/{id}")
    public PlaceStateReportResponse get(@PathVariable Long id) {
        return placeStateReportService.get(id);
    }

    @Override
    @GetMapping("/places/{placeId}/state-reports")
    public List<PlaceStateReportResponse> findByPlace(@PathVariable Long placeId) {
        return placeStateReportService.listByPlace(placeId);
    }
}
