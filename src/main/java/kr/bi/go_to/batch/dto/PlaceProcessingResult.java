package kr.bi.go_to.batch.dto;

import kr.bi.go_to.model.place.Place;

public record PlaceProcessingResult(
        Place place,
        String bfDetails,
        String introDetails,
        boolean detailCommonSynced,
        boolean detailWithTourSynced,
        boolean detailIntroSynced) {

    public PlaceProcessingResult(Place place, String bfDetails, String introDetails) {
        this(place, bfDetails, introDetails, false, false, false);
    }
}
