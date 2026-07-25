package kr.bi.go_to.controller.place;

import jakarta.validation.Valid;
import kr.bi.go_to.controller.place.request.PlaceSearchRequest;
import kr.bi.go_to.controller.place.response.PlaceSearchResponse;
import kr.bi.go_to.spec.PlaceApiSpec;
import kr.bi.go_to.usecase.SearchPlacesUseCase;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController implements PlaceApiSpec {

    private final SearchPlacesUseCase searchPlacesUseCase;

    public PlaceController(SearchPlacesUseCase searchPlacesUseCase) {
        this.searchPlacesUseCase = searchPlacesUseCase;
    }

    @Override
    @GetMapping("/search")
    public PlaceSearchResponse search(@Valid @ParameterObject @ModelAttribute PlaceSearchRequest request) {
        return searchPlacesUseCase.execute(request);
    }
}
