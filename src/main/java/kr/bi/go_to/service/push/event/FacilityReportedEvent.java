package kr.bi.go_to.service.push.event;

/** 시설(엘리베이터·화장실 등) 상태 제보가 등록됐다. 그 시설이 속한 장소를 저장한 사람들에게 알린다. */
public record FacilityReportedEvent(
        Long placeId, String placeName, String facilityName, String issueLabel, Long reporterId) {}
