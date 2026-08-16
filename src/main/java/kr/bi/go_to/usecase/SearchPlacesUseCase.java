package kr.bi.go_to.usecase;

import java.util.List;
import kr.bi.go_to.controller.place.request.PlaceSearchRequest;
import kr.bi.go_to.controller.place.response.AppliedFiltersResponse;
import kr.bi.go_to.controller.place.response.BfDetailsResponse;
import kr.bi.go_to.controller.place.response.PlaceFilterResponse;
import kr.bi.go_to.controller.place.response.PlaceSearchItemResponse;
import kr.bi.go_to.controller.place.response.PlaceSearchResponse;
import kr.bi.go_to.service.place.PlaceService;
import kr.bi.go_to.service.place.model.PlaceData;
import org.springframework.stereotype.Component;

@Component
public class SearchPlacesUseCase {

    private final PlaceService placeService;

    public SearchPlacesUseCase(PlaceService placeService) {
        this.placeService = placeService;
    }

    public PlaceSearchResponse execute(PlaceSearchRequest request) {
        // categoryPrefixes/mobilityTypes/avoid는 DbPlaceService 구현 전까지 no-op이다 (ADR-0004).
        List<PlaceSearchItemResponse> places =
                placeService.searchNearby(request.lat(), request.lng(), request.k(), request.categoryCode()).stream()
                        .map(this::toResponse)
                        .toList();
        List<String> categories = placeService.findDistinctCategories();

        AppliedFiltersResponse appliedFilters =
                new AppliedFiltersResponse(request.categoryPrefixes(), request.mobilityTypes(), request.avoid());

        return new PlaceSearchResponse(places, new PlaceFilterResponse(categories), appliedFilters);
    }

    private PlaceSearchItemResponse toResponse(PlaceData place) {
        return new PlaceSearchItemResponse(
                place.id(),
                place.name(),
                place.categoryCode(),
                place.sanitizedAddress(),
                place.thumbnailUrl(),
                place.latitude(),
                place.longitude(),
                Math.round(place.distanceMeters() * 10.0) / 10.0,
                BfDetailsResponse.from(place.bfDetails()),
                place.hasIndoorMap());
    }
}
