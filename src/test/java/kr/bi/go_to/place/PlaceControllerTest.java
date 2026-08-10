package kr.bi.go_to.place;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.bi.go_to.controller.place.PlaceController;
import kr.bi.go_to.service.obstaclereport.NearbyObstacleSummary;
import kr.bi.go_to.service.obstaclereport.ObstacleReportService;
import kr.bi.go_to.service.place.mock.MockPlaceService;
import kr.bi.go_to.service.savedplace.SavedPlaceService;
import kr.bi.go_to.usecase.GetNearbyAccessibilitySummaryUseCase;
import kr.bi.go_to.usecase.SearchPlacesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class PlaceControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ObstacleReportService obstacleReportService = mock(ObstacleReportService.class);
        when(obstacleReportService.getNearbySummary(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new NearbyObstacleSummary(0, 0, 0, 0));
        PlaceController controller = new PlaceController(
                new SearchPlacesUseCase(new MockPlaceService()),
                new GetNearbyAccessibilitySummaryUseCase(obstacleReportService),
                mock(SavedPlaceService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    void searchesWithDefaultLimitAndEchoesAppliedFilters() throws Exception {
        mockMvc.perform(get("/api/v1/places/search")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("categoryPrefixes", "관광지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(6))
                .andExpect(jsonPath("$.filters.categories.length()").value(3))
                .andExpect(jsonPath("$.appliedFilters.categoryPrefixes[0]").value("관광지"));
    }

    @Test
    void rejectsOutOfRangeCoordinatesAndLimit() throws Exception {
        mockMvc.perform(get("/api/v1/places/search")
                        .param("lat", "91")
                        .param("lng", "126.9780")
                        .param("k", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresCoordinates() throws Exception {
        mockMvc.perform(get("/api/v1/places/search")).andExpect(status().isBadRequest());
    }

    @Test
    void returnsNearbyAccessibilitySummary() throws Exception {
        mockMvc.perform(get("/api/v1/places/nearby-summary")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detourRecommendedCount").value(0))
                .andExpect(jsonPath("$.cautionCount").value(0))
                .andExpect(jsonPath("$.safeCount").value(0))
                .andExpect(jsonPath("$.needsConfirmationCount").value(0));
    }

    @Test
    void requiresCoordinatesForNearbySummary() throws Exception {
        mockMvc.perform(get("/api/v1/places/nearby-summary")).andExpect(status().isBadRequest());
    }
}
