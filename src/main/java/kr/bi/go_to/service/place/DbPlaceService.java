package kr.bi.go_to.service.place;

import java.util.List;
import kr.bi.go_to.repository.PlaceSearchProjection;
import kr.bi.go_to.repository.PlaceSearchRepository;
import kr.bi.go_to.service.place.model.BfDetailsData;
import kr.bi.go_to.service.place.model.PlaceData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DbPlaceService implements PlaceService {

    private final PlaceSearchRepository placeSearchRepository;

    public DbPlaceService(PlaceSearchRepository placeSearchRepository) {
        this.placeSearchRepository = placeSearchRepository;
    }

    @Override
    public List<PlaceData> searchNearby(double latitude, double longitude, int limit, String category) {
        return placeSearchRepository.searchNearby(latitude, longitude, limit, category).stream()
                .map(this::toData)
                .toList();
    }

    @Override
    public List<String> findDistinctCategories() {
        return placeSearchRepository.findDistinctCategories();
    }

    private PlaceData toData(PlaceSearchProjection place) {
        return new PlaceData(
                place.getId(),
                place.getExternalId(),
                place.getSource(),
                place.getCategory(),
                place.getName(),
                place.getSanitizedAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getThumbnailUrl(),
                new BfDetailsData(
                        Boolean.TRUE.equals(place.getHasElevator()),
                        Boolean.TRUE.equals(place.getHasAccessibleToilet()),
                        Boolean.TRUE.equals(place.getHasRamp())),
                place.getBfLastSyncedAt(),
                place.getDistanceMeters(),
                Boolean.TRUE.equals(place.getHasIndoorMap()));
    }
}
