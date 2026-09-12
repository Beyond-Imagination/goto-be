package kr.bi.go_to.service.push.event;

import java.util.List;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpKind;

/** 도움 요청이 올라왔다. 요청 위치 반경 안에 있는 기기들에 알린다. */
public record HelpRequestCreatedEvent(
        UUID helpRequestId,
        Long requesterId,
        double latitude,
        double longitude,
        String locationLabel,
        List<HelpKind> kinds) {}
