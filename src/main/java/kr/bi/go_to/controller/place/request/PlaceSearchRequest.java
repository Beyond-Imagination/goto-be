package kr.bi.go_to.controller.place.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import kr.bi.go_to.enums.MobilityType;
import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;

@Schema(name = "PlaceSearchRequest", description = "Place search request")
public record PlaceSearchRequest(
        @Schema(description = "Current latitude", example = "37.5665", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-90.0")
                @DecimalMax("90.0")
                Double lat,
        @Schema(description = "Current longitude", example = "126.9780", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                Double lng,
        @Schema(description = "Number of places to return, default 10, max 50", example = "10", defaultValue = "10")
                @Min(1)
                @Max(50)
                Integer k,
        @Schema(description = "Tour API leaf category code filter", example = "A01010100") String categoryCode,
        @Schema(description = "Place name or address keyword", example = "museum") String keyword,
        @Schema(description = "Place type filter prefixes. Currently echoed only until DbPlaceService supports it.")
                Set<String> categoryPrefixes,
        @Schema(description = "Mobility filters. Currently echoed only until DbPlaceService supports it.")
                Set<MobilityType> mobilityTypes,
        @Schema(description = "Obstacle issue filters. Currently echoed only until DbPlaceService supports it.")
                Set<ObstacleIssueType> avoid) {
    public PlaceSearchRequest {
        k = k == null ? 10 : k;
        categoryCode = categoryCode == null || categoryCode.isBlank() ? null : categoryCode.trim();
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        categoryPrefixes = categoryPrefixes == null ? Set.of() : Set.copyOf(categoryPrefixes);
        mobilityTypes = mobilityTypes == null ? Set.of() : Set.copyOf(mobilityTypes);
        avoid = avoid == null ? Set.of() : Set.copyOf(avoid);
    }
}
