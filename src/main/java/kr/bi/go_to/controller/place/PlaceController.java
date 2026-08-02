package kr.bi.go_to.controller.place;

import jakarta.validation.Valid;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.place.request.PlaceSearchRequest;
import kr.bi.go_to.controller.place.response.PlaceSearchResponse;
import kr.bi.go_to.service.savedplace.SavedPlaceService;
import kr.bi.go_to.spec.PlaceApiSpec;
import kr.bi.go_to.usecase.SearchPlacesUseCase;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController implements PlaceApiSpec {

    private final SearchPlacesUseCase searchPlacesUseCase;
    private final SavedPlaceService savedPlaceService;

    public PlaceController(SearchPlacesUseCase searchPlacesUseCase, SavedPlaceService savedPlaceService) {
        this.searchPlacesUseCase = searchPlacesUseCase;
        this.savedPlaceService = savedPlaceService;
    }

    @Override
    @GetMapping("/search")
    public PlaceSearchResponse search(@Valid @ParameterObject @ModelAttribute PlaceSearchRequest request) {
        return searchPlacesUseCase.execute(request);
    }

    @Override
    @PostMapping("/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long id) {
        savedPlaceService.save(member.id(), id);
    }

    @Override
    @DeleteMapping("/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long id) {
        savedPlaceService.unsave(member.id(), id);
    }
}
