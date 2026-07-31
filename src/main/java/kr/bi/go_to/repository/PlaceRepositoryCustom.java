package kr.bi.go_to.repository;

import java.util.List;
import kr.bi.go_to.model.place.Place;

public interface PlaceRepositoryCustom {

    List<Place> findNearbyActivePlaces(double latitude, double longitude, int radiusMeters, int limit);
}
