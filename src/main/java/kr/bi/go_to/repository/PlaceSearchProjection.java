package kr.bi.go_to.repository;

import java.time.Instant;

public interface PlaceSearchProjection {

    Long getId();

    String getExternalId();

    String getSource();

    String getCategory();

    String getName();

    String getSanitizedAddress();

    Double getLatitude();

    Double getLongitude();

    String getThumbnailUrl();

    Boolean getHasElevator();

    Boolean getHasAccessibleToilet();

    Boolean getHasRamp();

    Instant getBfLastSyncedAt();

    Double getDistanceMeters();

    Boolean getHasIndoorMap();
}
