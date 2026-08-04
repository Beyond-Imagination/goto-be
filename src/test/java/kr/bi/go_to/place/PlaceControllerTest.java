package kr.bi.go_to.place;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.bi.go_to.controller.place.PlaceController;
import kr.bi.go_to.exception.GlobalExceptionHandler;
import kr.bi.go_to.service.place.mock.MockPlaceService;
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
        PlaceController controller = new PlaceController(new SearchPlacesUseCase(new MockPlaceService()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void searchesWithDefaultLimitAndCategoryFilter() throws Exception {
        mockMvc.perform(get("/api/v1/places/search")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("categoryCode", "A01010100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(3))
                .andExpect(jsonPath("$.places[0].name").value("경복궁"))
                .andExpect(jsonPath("$.places[0].categoryCode").value("A01010100"))
                .andExpect(jsonPath("$.places[0].category").doesNotExist())
                .andExpect(jsonPath("$.filters.categoryCodes.length()").value(3))
                .andExpect(jsonPath("$.filters.categories").doesNotExist());
    }

    @Test
    void rejectsLegacyCategoryParameter() throws Exception {
        mockMvc.perform(get("/api/v1/places/search")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("category", "A01010100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_QUERY_PARAMETER"));
    }

    @Test
    void rejectsLegacyCategoryWhenCategoryCodeIsAlsoPresent() throws Exception {
        mockMvc.perform(get("/api/v1/places/search")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("category", "")
                        .param("categoryCode", "A01010100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_QUERY_PARAMETER"));
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
}
