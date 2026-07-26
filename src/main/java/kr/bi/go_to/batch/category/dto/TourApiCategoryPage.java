package kr.bi.go_to.batch.category.dto;

import java.util.List;

public record TourApiCategoryPage(int pageNo, int numOfRows, int totalCount, List<TourApiCategoryItem> items) {

    public TourApiCategoryPage {
        items = List.copyOf(items);
    }
}
