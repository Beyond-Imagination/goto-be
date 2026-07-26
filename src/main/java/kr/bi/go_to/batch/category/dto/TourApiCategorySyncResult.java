package kr.bi.go_to.batch.category.dto;

import java.util.UUID;

public record TourApiCategorySyncResult(
        UUID syncToken, int pageCount, int largeCount, int middleCount, int smallCount) {}
