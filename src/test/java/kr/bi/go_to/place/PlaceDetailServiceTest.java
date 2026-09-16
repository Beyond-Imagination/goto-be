package kr.bi.go_to.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.bi.go_to.controller.place.response.PlaceDetailResponse;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.place.PlaceBfDetails;
import kr.bi.go_to.model.place.PlaceBfInfo;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.placereport.PlaceFacilityStatus;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.repository.PlaceBfInfoRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.service.place.PlaceDetailService;
import org.junit.jupiter.api.Test;

class PlaceDetailServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceBfInfoRepository placeBfInfoRepository = mock(PlaceBfInfoRepository.class);
    private final PlaceStateReportRepository placeStateReportRepository = mock(PlaceStateReportRepository.class);
    private final PlaceDetailService service =
            new PlaceDetailService(placeRepository, placeBfInfoRepository, placeStateReportRepository);

    @Test
    void returnsWarningWhenOfficialOrRecentStateNeedsAttention() {
        Place place = place();
        when(placeRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(place));
        when(placeBfInfoRepository.findById(1L))
                .thenReturn(Optional.of(new PlaceBfInfo(place, bfDetails(false, true, true))));
        when(placeStateReportRepository.findLatestByPlace(1L, 20)).thenReturn(List.of(report(place)));

        PlaceDetailResponse response = service.getDetail(1L);

        assertThat(response.detailState()).isEqualTo(PlaceDetailResponse.DetailState.WARNING);
        assertThat(response.badges())
                .extracting(PlaceDetailResponse.Badge::text)
                .contains("주의 필요");
        assertThat(response.issues()).hasSize(1);
        assertThat(response.accessibilityRows())
                .filteredOn(row -> row.key() == PlaceDetailResponse.RowKey.ENTRANCE)
                .first()
                .extracting(row -> row.official().status())
                .isEqualTo(PlaceDetailResponse.RowStatus.UNAVAILABLE);
    }

    @Test
    void returnsReportMissingWhenOfficialInfoExistsButThereIsNoRecentReport() {
        Place place = place();
        when(placeRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(place));
        when(placeBfInfoRepository.findById(1L))
                .thenReturn(Optional.of(new PlaceBfInfo(place, bfDetails(true, true, true))));
        when(placeStateReportRepository.findLatestByPlace(1L, 20)).thenReturn(List.of());

        PlaceDetailResponse response = service.getDetail(1L);

        assertThat(response.detailState()).isEqualTo(PlaceDetailResponse.DetailState.REPORT_MISSING);
        assertThat(response.accessibilityRows())
                .allMatch(row -> row.recent().status() == PlaceDetailResponse.RowStatus.NO_REPORT);
    }

    @Test
    void returnsOfficialMissingWhenNoOfficialInfoExistsButRecentReportExists() {
        Place place = place();
        when(placeRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(place));
        when(placeBfInfoRepository.findById(1L)).thenReturn(Optional.empty());
        when(placeStateReportRepository.findLatestByPlace(1L, 20)).thenReturn(List.of(accessibleReport(place)));

        PlaceDetailResponse response = service.getDetail(1L);

        assertThat(response.detailState()).isEqualTo(PlaceDetailResponse.DetailState.OFFICIAL_MISSING);
        assertThat(response.accessibilityRows())
                .allMatch(row -> row.official().status() == PlaceDetailResponse.RowStatus.NO_OFFICIAL);
    }

    @Test
    void failsWhenPlaceDoesNotExist() {
        when(placeRepository.findByIdAndIsDeletedFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
    }

    private Place place() {
        return Place.builder()
                .externalId("place-1")
                .source("TEST")
                .categoryCode("A01010100")
                .name("서울숲")
                .sanitizedAddress("서울 성동구")
                .thumbnailUrl("https://example.test/place.jpg")
                .build();
    }

    private PlaceBfDetails bfDetails(boolean route, boolean elevator, boolean restroom) {
        PlaceBfDetails details = new PlaceBfDetails();
        details.setMobility(Map.of(
                "route", new PlaceBfDetails.BfItem(route, null, "경사로 정보"),
                "elevator", new PlaceBfDetails.BfItem(elevator, null, "엘리베이터 정보"),
                "restroom", new PlaceBfDetails.BfItem(restroom, null, "화장실 정보")));
        return details;
    }

    private PlaceStateReport report(Place place) {
        return PlaceStateReport.builder()
                .place(place)
                .reporter(new Member(Role.USER, "reporter"))
                .accessStatus(PlaceAccessStatus.PARTIALLY_ACCESSIBLE)
                .facilityStatuses(Map.of(PriorityFacility.ELEVATOR, PlaceFacilityStatus.BROKEN))
                .description("엘리베이터가 고장났어요")
                .build();
    }

    private PlaceStateReport accessibleReport(Place place) {
        return PlaceStateReport.builder()
                .place(place)
                .reporter(new Member(Role.USER, "reporter"))
                .accessStatus(PlaceAccessStatus.ACCESSIBLE)
                .build();
    }
}
