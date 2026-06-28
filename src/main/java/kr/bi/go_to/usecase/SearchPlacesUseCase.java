package kr.bi.go_to.usecase;

import java.util.Comparator;
import java.util.List;
import kr.bi.go_to.controller.place.request.PlaceSearchRequest;
import kr.bi.go_to.controller.place.response.BfDetailsResponse;
import kr.bi.go_to.controller.place.response.PlaceFilterResponse;
import kr.bi.go_to.controller.place.response.PlaceSearchItemResponse;
import kr.bi.go_to.controller.place.response.PlaceSearchResponse;
import kr.bi.go_to.service.place.PlaceService;
import kr.bi.go_to.service.place.model.PlaceData;
import org.springframework.stereotype.Component;

@Component
public class SearchPlacesUseCase {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final PlaceService placeService;

    public SearchPlacesUseCase(PlaceService placeService) {
        this.placeService = placeService;
    }

    public PlaceSearchResponse execute(PlaceSearchRequest request) {
        List<PlaceData> allPlaces = placeService.findAll();
        List<String> categories =
                allPlaces.stream().map(PlaceData::category).distinct().sorted().toList();
        List<PlaceSearchItemResponse> places = allPlaces.stream()
                .filter(place -> request.category() == null || place.category().equals(request.category()))
                .map(place -> toResponse(place, request.lat(), request.lng()))
                .sorted(Comparator.comparingDouble(PlaceSearchItemResponse::distanceMeters))
                .limit(request.k())
                .toList();

        return new PlaceSearchResponse(places, new PlaceFilterResponse(categories));
    }

    private PlaceSearchItemResponse toResponse(PlaceData place, double latitude, double longitude) {
        double distanceMeters = haversineDistance(latitude, longitude, place.latitude(), place.longitude());
        return new PlaceSearchItemResponse(
                place.id(),
                place.name(),
                place.category(),
                place.sanitizedAddress(),
                place.thumbnailUrl(),
                place.latitude(),
                place.longitude(),
                Math.round(distanceMeters * 10.0) / 10.0,
                BfDetailsResponse.from(place.bfDetails()));
    }

    private double haversineDistance(double latitude1, double longitude1, double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double startLatitude = Math.toRadians(latitude1);
        double endLatitude = Math.toRadians(latitude2);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude) * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(haversine));
    }
}
