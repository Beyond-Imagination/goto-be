package kr.bi.go_to.service.push.event;

import kr.bi.go_to.model.placereport.PlaceAccessStatus;

/**
 * 장소 상태 제보가 등록됐다. 그 장소를 저장한 사람들에게 알린다.
 *
 * <p>제보 트랜잭션이 커밋된 뒤에 처리된다 — 롤백된 제보로 푸시가 나가면 안 된다.
 */
public record PlaceStateReportedEvent(
        Long placeId, String placeName, Long reporterId, PlaceAccessStatus accessStatus) {}
