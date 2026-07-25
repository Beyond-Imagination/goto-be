package kr.bi.go_to.service.report.model;

import java.time.Instant;

public record ReportData(
        Long id, Long reporterId, FacilityNodeData node, String issueType, String description, Instant createdAt) {}
