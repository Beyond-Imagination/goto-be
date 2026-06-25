package kr.bi.go_to.batch.dto;

import kr.bi.go_to.model.place.Place;

public record PlaceProcessingResult(Place place, String bfDetails, String introDetails) {}
