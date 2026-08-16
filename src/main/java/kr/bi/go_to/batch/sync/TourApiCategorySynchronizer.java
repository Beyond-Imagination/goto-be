package kr.bi.go_to.batch.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.bi.go_to.batch.client.TourApiCategoryClient;
import kr.bi.go_to.batch.dto.TourApiCategoryPage;
import kr.bi.go_to.batch.dto.TourApiCategorySnapshot;
import kr.bi.go_to.batch.dto.TourApiCategorySyncResult;
import kr.bi.go_to.batch.repository.TourApiCategoryRepository;
import kr.bi.go_to.batch.validation.TourApiCategorySnapshotValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TourApiCategorySynchronizer {

    private final TourApiCategoryClient client;
    private final TourApiCategorySnapshotValidator validator;
    private final TourApiCategoryRepository repository;
    private final int pageSize;

    public TourApiCategorySynchronizer(
            TourApiCategoryClient client,
            TourApiCategorySnapshotValidator validator,
            TourApiCategoryRepository repository,
            @Value("${tour-api.category-page-size:1000}") int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("tour-api.category-page-size must be positive");
        }
        this.client = client;
        this.validator = validator;
        this.repository = repository;
        this.pageSize = pageSize;
    }

    public TourApiCategorySyncResult synchronize() {
        List<TourApiCategoryPage> pages = fetchCompleteSnapshot();
        TourApiCategorySnapshot snapshot = validator.validate(pages);
        UUID syncToken = UUID.randomUUID();

        repository.publish(snapshot.categories(), syncToken);

        int large = 0;
        int middle = 0;
        int small = 0;
        for (var category : snapshot.categories()) {
            if (category.depth() == 1) {
                large++;
            } else if (category.depth() == 2) {
                middle++;
            } else if (category.depth() == 3) {
                small++;
            }
        }
        return new TourApiCategorySyncResult(syncToken, snapshot.pageCount(), large, middle, small);
    }

    private List<TourApiCategoryPage> fetchCompleteSnapshot() {
        List<TourApiCategoryPage> pages = new ArrayList<>();
        int requestedPage = 1;
        int fetched = 0;
        Integer expectedTotal = null;

        do {
            TourApiCategoryPage page = client.fetchPage(requestedPage, pageSize);
            pages.add(page);
            if (expectedTotal == null) {
                expectedTotal = page.totalCount();
            }
            fetched += page.items().size();
            requestedPage++;

            if (page.items().isEmpty()) {
                break;
            }
        } while (fetched < expectedTotal);

        return pages;
    }
}
