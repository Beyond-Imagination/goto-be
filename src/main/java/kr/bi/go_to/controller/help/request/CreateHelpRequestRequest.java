package kr.bi.go_to.controller.help.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateHelpRequestRequest(
        Long placeId,
        @NotBlank @Size(max = 255) String locationLabel,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        Integer floorLevel,
        @Size(max = 500) String message,
        @Min(5) @Max(120) Integer expiresInMinutes) {}
