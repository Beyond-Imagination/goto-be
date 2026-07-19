package kr.bi.go_to.service.place.mock;

import java.time.Instant;
import java.util.List;
import kr.bi.go_to.service.place.PlaceService;
import kr.bi.go_to.service.place.model.BfDetailsData;
import kr.bi.go_to.service.place.model.PlaceData;
import org.springframework.stereotype.Service;

@Service
public class MockPlaceService implements PlaceService {

    private static final Instant LAST_SYNCED_AT = Instant.parse("2026-06-01T00:00:00Z");

    private static final List<PlaceData> PLACES = List.of(
            place(
                    1,
                    "tour-001",
                    "관광지",
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
                    "공공기관",
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
                    "관광지",
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
                    "숙박",
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
                    "관광지",
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
                    "공공기관",
                    "용산구청",
                    "서울 용산구 녹사평대로 150",
                    37.532389,
                    126.990463,
                    "https://example.com/yongsan-office.jpg",
                    true,
                    true,
                    true));

    @Override
    public List<PlaceData> findAll() {
        return PLACES;
    }

    private static PlaceData place(
            long id,
            String externalId,
            String category,
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
                category,
                name,
                address,
                latitude,
                longitude,
                thumbnailUrl,
                new BfDetailsData(hasElevator, hasAccessibleToilet, hasRamp),
                LAST_SYNCED_AT);
    }
}
