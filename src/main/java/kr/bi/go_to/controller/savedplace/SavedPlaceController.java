package kr.bi.go_to.controller.savedplace;

import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.savedplace.response.SavedPlaceResponse;
import kr.bi.go_to.service.savedplace.SavedPlaceService;
import kr.bi.go_to.spec.SavedPlaceApiSpec;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
