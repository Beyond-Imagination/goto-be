package kr.bi.go_to.service.push.event;

import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;

/** 제보가 "아직 있어요"로 확인됐다. 제보자에게 알린다(확인한 본인에게는 보내지 않는다). */
public record ObstacleReportConfirmedEvent(
        Long reportId, Long reporterId, Long confirmedByMemberId, ObstacleIssueType issueType, int confirmedCount) {}
