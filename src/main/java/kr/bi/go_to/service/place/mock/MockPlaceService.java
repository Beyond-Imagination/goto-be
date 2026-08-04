package kr.bi.go_to.service.place.mock;

import java.time.Instant;
import java.util.List;
import kr.bi.go_to.service.place.PlaceService;
import kr.bi.go_to.service.place.model.BfDetailsData;
import kr.bi.go_to.service.place.model.PlaceData;

public class MockPlaceService implements PlaceService {

    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final Instant LAST_SYNCED_AT = Instant.parse("2026-06-01T00:00:00Z");

    private static final List<PlaceData> PLACES = List.of(
            place(
                    1,
                    "tour-001",
                    "A01010100",
                    "국립중앙박물관",
                    "서울 용산구 서빙고로 137",
                    37.523850,
                    126.980470,
                    "https://example.com/museum.jpg",
                    true,
                    true,
                    true),
            place(
                    2,
                    "public-001",
                    "B02010100",
                    "서울도서관",
                    "서울 중구 세종대로 110",
                    37.566317,
                    126.977829,
                    "https://example.com/library.jpg",
                    true,
                    true,
                    true),
            place(
                    3,
                    "tour-002",
                    "A01010100",
                    "경복궁",
                    "서울 종로구 사직로 161",
                    37.579617,
                    126.977041,
                    "https://example.com/palace.jpg",
                    false,
                    true,
                    true),
            place(
                    4,
                    "stay-001",
                    "A05010100",
                    "서울 유스호스텔",
                    "서울 중구 퇴계로26가길 6",
                    37.558691,
                    126.990619,
                    "https://example.com/hostel.jpg",
                    true,
                    true,
                    true),
            place(
                    5,
                    "tour-003",
                    "A01010100",
                    "남산서울타워",
                    "서울 용산구 남산공원길 105",
                    37.551169,
                    126.988227,
                    "https://example.com/tower.jpg",
                    true,
                    true,
                    false),
            place(
                    6,
                    "public-002",
                    "B02010100",
                    "용산구청",
                    "서울 용산구 녹사평대로 150",
                    37.532389,
                    126.990463,
                    "https://example.com/yongsan-office.jpg",
                    true,
                    true,
                    true));

    @Override
    public List<PlaceData> searchNearby(double latitude, double longitude, int limit, String category) {
        return PLACES.stream()
                .filter(place -> category == null || category.equals(place.category()))
                .map(place -> withDistance(place, latitude, longitude))
                .sorted((left, right) -> Double.compare(left.distanceMeters(), right.distanceMeters()))
                .limit(limit)
                .toList();
    }

    @Override
    public List<String> findDistinctCategories() {
        return PLACES.stream()
                .map(PlaceData::category)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static PlaceData place(
            long id,
            String externalId,
            String categoryCode,
            String name,
            String address,
            double latitude,
            double longitude,
            String thumbnailUrl,
            boolean hasElevator,
            boolean hasAccessibleToilet,
            boolean hasRamp) {
        return new PlaceData(
                id,
                externalId,
                "MOCK",
                categoryCode,
                name,
                address,
                latitude,
                longitude,
                thumbnailUrl,
                new BfDetailsData(hasElevator, hasAccessibleToilet, hasRamp),
                LAST_SYNCED_AT,
                0);
    }

    private PlaceData withDistance(PlaceData place, double latitude, double longitude) {
        return new PlaceData(
                place.id(),
                place.externalId(),
                place.source(),
                place.category(),
                place.name(),
                place.sanitizedAddress(),
                place.latitude(),
                place.longitude(),
                place.thumbnailUrl(),
                place.bfDetails(),
                place.bfLastSyncedAt(),
                haversineDistance(latitude, longitude, place.latitude(), place.longitude()));
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
