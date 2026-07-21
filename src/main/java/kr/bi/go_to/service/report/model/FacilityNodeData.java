package kr.bi.go_to.service.report.model;

public record FacilityNodeData(
        Long id,
        String nodeType,
        String name,
        double latitude,
        double longitude,
        Integer floorLevel,
        boolean isCheckpoint,
        Integer snapRadius) {}
