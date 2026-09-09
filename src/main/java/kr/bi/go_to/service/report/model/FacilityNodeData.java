package kr.bi.go_to.service.report.model;

public record FacilityNodeData(
        Long id,
        String nodeType,
        String name,
        double latitude,
        double longitude,
        Integer floorLevel,
        boolean isCheckpoint,
        Integer snapRadius,
        /* 시설이 속한 장소. 시설 제보 화면이 「장소명 · 2층 엘리베이터」로 보여주는 데 쓴다. */
        Long placeId,
        String placeName) {}
