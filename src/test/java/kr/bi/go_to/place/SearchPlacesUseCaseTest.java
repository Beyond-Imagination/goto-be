package kr.bi.go_to.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import kr.bi.go_to.controller.place.request.PlaceSearchRequest;
import kr.bi.go_to.controller.place.response.PlaceSearchResponse;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.service.place.mock.MockPlaceService;
import kr.bi.go_to.usecase.SearchPlacesUseCase;
import org.junit.jupiter.api.Test;

class SearchPlacesUseCaseTest {

    private final SearchPlacesUseCase useCase = new SearchPlacesUseCase(new MockPlaceService());

    @Test
    void returnsPlacesInDistanceOrderAndAppliesLimit() {
        PlaceSearchResponse response = useCase.execute(new PlaceSearchRequest(37.5665, 126.9780, 3, null, null, null));

        assertThat(response.places()).hasSize(3);
        assertThat(response.places())
                .extracting(place -> place.distanceMeters())
                .isSorted();
        assertThat(response.places().getFirst().name()).isEqualTo("서울도서관");
        assertThat(response.filters().categories()).containsExactly("공공기관", "관광지", "숙박");
    }

    @Test
    void doesNotFilterResultsSinceCategoryPrefixesFilterIsNoOpUntilDbPlaceServiceExists() {
        PlaceSearchResponse response =
                useCase.execute(new PlaceSearchRequest(37.5665, 126.9780, 10, Set.of("관광지"), null, null));

        assertThat(response.places()).hasSize(6);
    }

    @Test
    void echoesAppliedFiltersFromRequest() {
        PlaceSearchResponse response = useCase.execute(new PlaceSearchRequest(
                37.5665,
                126.9780,
                10,
                Set.of("A02"),
                Set.of(MobilityType.WHEELCHAIR),
                Set.of(ObstacleIssueType.STAIRS)));

        assertThat(response.appliedFilters().categoryPrefixes()).containsExactly("A02");
        assertThat(response.appliedFilters().mobilityTypes()).containsExactly(MobilityType.WHEELCHAIR);
        assertThat(response.appliedFilters().avoid()).containsExactly(ObstacleIssueType.STAIRS);
    }

    @Test
    void echoesEmptyAppliedFiltersWhenNoneProvided() {
        PlaceSearchResponse response = useCase.execute(new PlaceSearchRequest(37.5665, 126.9780, 10, null, null, null));

        assertThat(response.appliedFilters().categoryPrefixes()).isEmpty();
        assertThat(response.appliedFilters().mobilityTypes()).isEmpty();
        assertThat(response.appliedFilters().avoid()).isEmpty();
    }

    @Test
    void usesTenAsDefaultLimit() {
        PlaceSearchRequest request = new PlaceSearchRequest(37.5665, 126.9780, null, null, null, null);

        assertThat(request.k()).isEqualTo(10);
    }
}
