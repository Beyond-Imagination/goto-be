package kr.bi.go_to.place;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.place.PlaceBfDetails;
import kr.bi.go_to.model.place.PlaceBfInfo;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceSearchProjection;
import kr.bi.go_to.repository.PlaceSearchRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    ClockConfig.class,
    JpaAuditConfig.class,
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlaceSearchRepositoryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    PlaceSearchRepository placeSearchRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("DELETE FROM place_bf_info").executeUpdate();
        placeRepository.deleteAll();
    }

    @Test
    void searchesNearbyPlacesInDistanceOrderWithLimitAndBfDetails() {
        Place near = savePlace("near", "museum", "Near Museum", 37.5666, 126.9781, false);
        Place middle = savePlace("middle", "museum", "Middle Museum", 37.5700, 126.9820, false);
        savePlace("far", "museum", "Far Museum", 37.6200, 127.0300, false);
        savePlace("deleted", "museum", "Deleted Museum", 37.5664, 126.9779, true);
        savePlace("hotel", "hotel", "Near Hotel", 37.5664, 126.9779, false);
        saveBfInfo(near, true, false, true);
        saveBfInfo(middle, false, true, false);

        List<PlaceSearchProjection> result = placeSearchRepository.searchNearby(37.5665, 126.9780, 2, "museum");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlaceSearchProjection::getName).containsExactly("Near Museum", "Middle Museum");
        assertThat(result).extracting(PlaceSearchProjection::getDistanceMeters).isSorted();
        assertThat(result.get(0).getHasElevator()).isTrue();
        assertThat(result.get(0).getHasAccessibleToilet()).isFalse();
        assertThat(result.get(0).getHasRamp()).isTrue();
        assertThat(result.get(0).getBfLastSyncedAt()).isNotNull();
    }

    @Test
    void findsDistinctCategoriesExcludingNullBlankAndDeletedPlaces() {
        savePlace("museum-1", "museum", "Museum 1", 37.5665, 126.9780, false);
        savePlace("museum-2", "museum", "Museum 2", 37.5666, 126.9781, false);
        savePlace("hotel", "hotel", "Hotel", 37.5667, 126.9782, false);
        savePlace("blank", " ", "Blank", 37.5668, 126.9783, false);
        savePlace("deleted", "park", "Deleted Park", 37.5669, 126.9784, true);
        savePlace("null", null, "No Category", 37.5670, 126.9785, false);

        List<String> categories = placeSearchRepository.findDistinctCategories();

        assertThat(categories).containsExactly("hotel", "museum");
    }

    private Place savePlace(
            String externalId, String category, String name, double latitude, double longitude, boolean deleted) {
        return placeRepository.save(Place.builder()
                .externalId(externalId)
                .source("TEST")
                .category(category)
                .name(name)
                .sanitizedAddress(name + " address")
                .locationPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude)))
                .thumbnailUrl("https://example.com/" + externalId + ".jpg")
                .isDeleted(deleted)
                .build());
    }

    private void saveBfInfo(Place place, boolean elevator, boolean restroom, boolean route) {
        PlaceBfDetails details = new PlaceBfDetails();
        details.setMobility(Map.of(
                "elevator", new PlaceBfDetails.BfItem(elevator, null, null),
                "restroom", new PlaceBfDetails.BfItem(restroom, null, null),
                "route", new PlaceBfDetails.BfItem(route, null, null)));
        entityManager.persist(new PlaceBfInfo(place, details));
        entityManager.flush();
    }
}
