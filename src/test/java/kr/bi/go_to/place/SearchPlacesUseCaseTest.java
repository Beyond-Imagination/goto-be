package kr.bi.go_to.place;

import static org.assertj.core.api.Assertions.assertThat;

import kr.bi.go_to.controller.place.request.PlaceSearchRequest;
import kr.bi.go_to.controller.place.response.PlaceSearchResponse;
import kr.bi.go_to.service.place.mock.MockPlaceService;
import kr.bi.go_to.usecase.SearchPlacesUseCase;
import org.junit.jupiter.api.Test;

class SearchPlacesUseCaseTest {

    private final SearchPlacesUseCase useCase = new SearchPlacesUseCase(new MockPlaceService());

    @Test
    void returnsPlacesInDistanceOrderAndAppliesLimit() {
        PlaceSearchResponse response = useCase.execute(new PlaceSearchRequest(37.5665, 126.9780, 3, null));

        assertThat(response.places()).hasSize(3);
        assertThat(response.places())
                .extracting(place -> place.distanceMeters())
                .isSorted();
        assertThat(response.places().getFirst().name()).isEqualTo("서울도서관");
        assertThat(response.filters().categoryCodes()).containsExactly("A01010100", "A05010100", "B02010100");
    }

    @Test
    void filtersByExactCategoryCodeBeforeApplyingLimit() {
        PlaceSearchResponse response = useCase.execute(new PlaceSearchRequest(37.5665, 126.9780, 2, " A01010100 "));

        assertThat(response.places()).hasSize(2);
        assertThat(response.places()).allMatch(place -> place.categoryCode().equals("A01010100"));
        assertThat(response.places()).extracting(place -> place.name()).containsExactly("경복궁", "남산서울타워");
    }

    @Test
    void doesNotMatchCategoryCodePrefixes() {
        PlaceSearchResponse response = useCase.execute(new PlaceSearchRequest(37.5665, 126.9780, 10, "A0101"));

        assertThat(response.places()).isEmpty();
    }

    @Test
    void usesTenAsDefaultLimit() {
        PlaceSearchRequest request = new PlaceSearchRequest(37.5665, 126.9780, null, null);

        assertThat(request.k()).isEqualTo(10);
    }
}
