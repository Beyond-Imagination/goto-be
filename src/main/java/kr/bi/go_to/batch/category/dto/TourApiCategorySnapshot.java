package kr.bi.go_to.batch.category.dto;

import java.util.List;

public record TourApiCategorySnapshot(int pageCount, List<TourApiCategory> categories) {

    public TourApiCategorySnapshot {
        categories = List.copyOf(categories);
    }
}
