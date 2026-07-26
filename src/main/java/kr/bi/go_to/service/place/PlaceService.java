package kr.bi.go_to.service.place;

import java.util.List;
import kr.bi.go_to.service.place.model.PlaceData;

public interface PlaceService {

    List<PlaceData> searchNearby(double latitude, double longitude, int limit, String category);

    List<String> findDistinctCategories();
}
