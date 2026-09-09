package kr.bi.go_to.placereport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.bi.go_to.controller.placereport.request.CreatePlaceStateReportRequest;
import kr.bi.go_to.controller.placereport.response.PlaceStateReportResponse;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.placereport.PlaceAccessStatus;
import kr.bi.go_to.model.placereport.PlaceFacilityStatus;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.PlaceStateReportRepository;
import kr.bi.go_to.service.MemberService;
import kr.bi.go_to.service.placereport.PlaceStateReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

class PlaceStateReportServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final PlaceStateReportRepository placeStateReportRepository = mock(PlaceStateReportRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final MemberService memberService = mock(MemberService.class);
    private final PlaceStateReportService service =
            new PlaceStateReportService(placeStateReportRepository, placeRepository, memberService);

    private final Member reporter = new Member(Role.USER, "장소제보자");

    private Place place() {
        return Place.builder()
                .externalId("place-1")
                .source("TEST")
                .name("서울숲 공원")
                .sanitizedAddress("서울 성동구 뚝섬로 273")
                .locationPoint(GEOMETRY_FACTORY.createPoint(new Coordinate(127.037, 37.544)))
                .build();
    }

    private CreatePlaceStateReportRequest request(String description) {
        return new CreatePlaceStateReportRequest(
                1L,
                PlaceAccessStatus.PARTIALLY_ACCESSIBLE,
                Map.of(PriorityFacility.ELEVATOR, PlaceFacilityStatus.BROKEN),
                List.of("https://cdn.example.test/a.jpg"),
                description);
    }

    @Test
    @DisplayName("장소 상태 제보를 만들면 장소 정보와 입력값이 응답에 담긴다")
    void createReturnsPlaceAndInput() {
        when(memberService.getUser(1L)).thenReturn(reporter);
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place()));
        when(placeStateReportRepository.save(any(PlaceStateReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlaceStateReportResponse response = service.create(1L, request("정문 경사로가 있지만 문이 무거워요"));

        assertThat(response.placeName()).isEqualTo("서울숲 공원");
        assertThat(response.placeAddress()).isEqualTo("서울 성동구 뚝섬로 273");
        assertThat(response.latitude()).isEqualTo(37.544);
        assertThat(response.longitude()).isEqualTo(127.037);
        assertThat(response.accessStatus()).isEqualTo(PlaceAccessStatus.PARTIALLY_ACCESSIBLE);
        assertThat(response.facilityStatuses())
                .containsExactly(Map.entry(PriorityFacility.ELEVATOR, PlaceFacilityStatus.BROKEN));
        assertThat(response.photoUrls()).containsExactly("https://cdn.example.test/a.jpg");
        assertThat(response.description()).isEqualTo("정문 경사로가 있지만 문이 무거워요");
    }

    @Test
    @DisplayName("빈 메모는 null로 저장된다")
    void blankDescriptionBecomesNull() {
        when(memberService.getUser(1L)).thenReturn(reporter);
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place()));
        when(placeStateReportRepository.save(any(PlaceStateReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(1L, request("   ")).description()).isNull();
    }

    @Test
    @DisplayName("편의시설 상태를 보내지 않아도 제보할 수 있다")
    void createWithoutFacilityStatuses() {
        when(memberService.getUser(1L)).thenReturn(reporter);
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place()));
        when(placeStateReportRepository.save(any(PlaceStateReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlaceStateReportResponse response = service.create(
                1L, new CreatePlaceStateReportRequest(1L, PlaceAccessStatus.ACCESSIBLE, null, null, null));

        assertThat(response.facilityStatuses()).isEmpty();
        assertThat(response.photoUrls()).isEmpty();
    }

    @Test
    @DisplayName("없는 장소로 제보하면 404이며 저장하지 않는다")
    void createFailsWhenPlaceMissing() {
        when(memberService.getUser(1L)).thenReturn(reporter);
        when(placeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, request("메모")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
        verify(placeStateReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("삭제된 장소로는 제보할 수 없다")
    void createFailsWhenPlaceDeleted() {
        Place deleted = Place.builder()
                .externalId("place-1")
                .source("TEST")
                .name("사라진 장소")
                .isDeleted(true)
                .build();
        when(memberService.getUser(1L)).thenReturn(reporter);
        when(placeRepository.findById(1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.create(1L, request("메모")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
        verify(placeStateReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 제보를 상세 조회하면 404다")
    void getFailsWhenReportMissing() {
        when(placeStateReportRepository.findByIdWithPlace(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_STATE_REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 장소의 목록을 조회하면 404이며 제보를 조회하지 않는다")
    void listFailsWhenPlaceMissing() {
        when(placeRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.listByPlace(9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
        verify(placeStateReportRepository, never()).findLatestByPlace(anyLong(), anyInt());
    }

    @Test
    @DisplayName("장소별 목록은 조회 결과를 그대로 응답으로 변환한다")
    void listByPlaceMapsResults() {
        when(placeRepository.existsById(1L)).thenReturn(true);
        when(placeStateReportRepository.findLatestByPlace(1L, 20))
                .thenReturn(List.of(PlaceStateReport.builder()
                        .place(place())
                        .reporter(reporter)
                        .accessStatus(PlaceAccessStatus.INACCESSIBLE)
                        .photoUrls(List.of())
                        .build()));

        List<PlaceStateReportResponse> responses = service.listByPlace(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).accessStatus()).isEqualTo(PlaceAccessStatus.INACCESSIBLE);
        assertThat(responses.get(0).placeName()).isEqualTo("서울숲 공원");
    }
}
