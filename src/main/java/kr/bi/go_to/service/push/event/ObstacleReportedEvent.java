package kr.bi.go_to.service.push.event;

import kr.bi.go_to.model.obstaclereport.ObstacleIssueType;
import kr.bi.go_to.model.obstaclereport.ObstacleSeverity;

/** 장애물 제보가 등록됐다. 이 좌표 근처에 저장 장소를 둔 사람들에게 알린다. */
public record ObstacleReportedEvent(
        Long reportId,
        Long reporterId,
        double latitude,
        double longitude,
        ObstacleIssueType issueType,
        ObstacleSeverity severity) {}
