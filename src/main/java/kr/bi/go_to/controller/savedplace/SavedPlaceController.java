package kr.bi.go_to.controller.savedplace;

import jakarta.validation.Valid;
import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.savedplace.request.UpdateSavedPlaceNotificationRequest;
import kr.bi.go_to.controller.savedplace.response.SavedPlaceResponse;
import kr.bi.go_to.service.savedplace.SavedPlaceService;
import kr.bi.go_to.spec.SavedPlaceApiSpec;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저장 장소 API.
 * 저장/해제 자체는 장소 상세·검색 결과의 하트이므로 장소 하위 경로
 * ({@code POST|DELETE /api/v1/places/{id}/save})가 담당하고, 여기서는 저장 목록과
 * 장소별 알림 스위치를 다룬다.
 */
@RestController
@RequestMapping("/api/v1/saved-places")
public class SavedPlaceController implements SavedPlaceApiSpec {

    private final SavedPlaceService savedPlaceService;

    public SavedPlaceController(SavedPlaceService savedPlaceService) {
        this.savedPlaceService = savedPlaceService;
    }

    @Override
    @GetMapping("/me")
    public List<SavedPlaceResponse> findMine(@AuthenticationPrincipal AuthenticatedMember member) {
        return savedPlaceService.listMine(member.id());
    }

    @Override
    @PatchMapping("/{placeId}/notification")
    public SavedPlaceResponse updateNotification(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long placeId,
            @Valid @RequestBody UpdateSavedPlaceNotificationRequest request) {
        return savedPlaceService.updateNotification(member.id(), placeId, request.enabled());
    }
}
