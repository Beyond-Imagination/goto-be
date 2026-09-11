package kr.bi.go_to.service.push.event;

import java.util.UUID;

/** 도우미가 요청을 수락했다. 요청자에게 알린다. */
public record HelpRequestAcceptedEvent(UUID helpRequestId, Long requesterId, String helperNickname) {}
