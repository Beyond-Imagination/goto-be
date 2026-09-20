package kr.bi.go_to.controller.place.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "PlaceDetailResponse", description = "Place detail response for the bottom detail tab")
public record PlaceDetailResponse(
        Long placeId,
        String name,
        String address,
        String category,
        String categoryCode,
        List<String> thumbnailUrls,
        DetailState detailState,
        List<Badge> badges,
        Summary summary,
        List<Issue> issues,
        List<AccessibilityRow> accessibilityRows,
        String notice) {

    public enum DetailState {
        NORMAL,
        WARNING,
        OFFICIAL_MISSING,
        REPORT_MISSING
    }

    public enum RowKey {
        ENTRANCE,
        ELEVATOR,
        ACCESSIBLE_TOILET,
        PARKING,
        NURSING_ROOM
    }

    public enum RowStatus {
        AVAILABLE,
        WARNING,
        UNAVAILABLE,
        BROKEN,
        NO_OFFICIAL,
        NO_REPORT
    }

    public record Badge(String text, String tone) {}

    public record Summary(String title, String description) {}

    public record Issue(
            Long id, String title, String reportedAtLabel, Instant createdAt, int confirmCount, String status) {}

    public record AccessibilityRow(RowKey key, String label, RowValue official, RowValue recent) {}

    public record RowValue(RowStatus status, String text, String description, boolean reportCtaEnabled) {}
}
