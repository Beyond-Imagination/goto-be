package kr.bi.go_to.batch.category.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.bi.go_to.batch.category.dto.TourApiCategory;
import kr.bi.go_to.batch.category.dto.TourApiCategoryItem;
import kr.bi.go_to.batch.category.dto.TourApiCategoryPage;
import kr.bi.go_to.batch.category.dto.TourApiCategorySnapshot;
import kr.bi.go_to.batch.category.exception.TourApiCategorySnapshotException;
import org.springframework.stereotype.Component;

@Component
public class TourApiCategorySnapshotValidator {

    public TourApiCategorySnapshot validate(List<TourApiCategoryPage> pages) {
        if (pages.isEmpty()) {
            throw invalid("No taxonomy page was returned");
        }

        int expectedTotal = pages.get(0).totalCount();
        if (expectedTotal <= 0) {
            throw invalid("Taxonomy totalCount must be positive");
        }
        int fetchedItems = 0;
        Set<Integer> pageNumbers = new HashSet<>();
        Map<String, TourApiCategory> categories = new LinkedHashMap<>();

        for (int index = 0; index < pages.size(); index++) {
            TourApiCategoryPage page = pages.get(index);
            int expectedPageNo = index + 1;
            if (page.pageNo() != expectedPageNo || !pageNumbers.add(page.pageNo())) {
                throw invalid("Wrong or repeated taxonomy page: " + page.pageNo());
            }
            if (page.numOfRows() <= 0) {
                throw invalid("Taxonomy numOfRows must be positive");
            }
            if (page.totalCount() != expectedTotal) {
                throw invalid("Taxonomy totalCount changed during traversal");
            }
            if (page.items().isEmpty() && fetchedItems < expectedTotal) {
                throw invalid("Taxonomy traversal ended with an early empty page");
            }
            if (page.items().size() > page.numOfRows()) {
                throw invalid("Taxonomy page contains more items than numOfRows");
            }

            for (TourApiCategoryItem item : page.items()) {
                addTuple(categories, item);
            }
            fetchedItems += page.items().size();

            boolean finalPage = fetchedItems >= expectedTotal;
            if (!finalPage && page.items().size() < page.numOfRows()) {
                throw invalid("Taxonomy traversal returned an early short page");
            }
            if (finalPage && fetchedItems != expectedTotal) {
                throw invalid("Taxonomy traversal item count exceeds totalCount");
            }
        }

        if (fetchedItems != expectedTotal) {
            throw invalid("Taxonomy traversal did not cover totalCount");
        }
        if (categories.isEmpty()) {
            throw invalid("Taxonomy snapshot must not be empty");
        }

        validateParents(categories);
        validateAcyclic(categories);
        return new TourApiCategorySnapshot(pages.size(), new ArrayList<>(categories.values()));
    }

    private void addTuple(Map<String, TourApiCategory> categories, TourApiCategoryItem item) {
        requireComplete(item);
        add(categories, new TourApiCategory(item.lclsSystm1Cd(), null, (short) 1, item.lclsSystm1Nm()));
        add(categories, new TourApiCategory(item.lclsSystm2Cd(), item.lclsSystm1Cd(), (short) 2, item.lclsSystm2Nm()));
        add(categories, new TourApiCategory(item.lclsSystm3Cd(), item.lclsSystm2Cd(), (short) 3, item.lclsSystm3Nm()));
    }

    private void requireComplete(TourApiCategoryItem item) {
        if (isBlank(item.lclsSystm1Cd())
                || isBlank(item.lclsSystm1Nm())
                || isBlank(item.lclsSystm2Cd())
                || isBlank(item.lclsSystm2Nm())
                || isBlank(item.lclsSystm3Cd())
                || isBlank(item.lclsSystm3Nm())) {
            throw invalid("Taxonomy tuple is incomplete");
        }
    }

    private void add(Map<String, TourApiCategory> categories, TourApiCategory category) {
        TourApiCategory existing = categories.putIfAbsent(category.code(), category);
        if (existing != null && !existing.equals(category)) {
            throw invalid("Conflicting taxonomy definition for code " + category.code());
        }
    }

    private void validateParents(Map<String, TourApiCategory> categories) {
        for (TourApiCategory category : categories.values()) {
            if (category.depth() == 1) {
                if (category.parentCode() != null) {
                    throw invalid("Depth-1 taxonomy category has a parent");
                }
                continue;
            }

            TourApiCategory parent = categories.get(category.parentCode());
            if (parent == null || parent.depth() != category.depth() - 1) {
                throw invalid("Taxonomy category has no valid returned parent: " + category.code());
            }
        }
    }

    private void validateAcyclic(Map<String, TourApiCategory> categories) {
        Map<String, VisitState> states = new HashMap<>();
        for (String code : categories.keySet()) {
            visit(code, categories, states);
        }
    }

    private void visit(String code, Map<String, TourApiCategory> categories, Map<String, VisitState> states) {
        VisitState state = states.get(code);
        if (state == VisitState.VISITING) {
            throw invalid("Taxonomy hierarchy contains a cycle at " + code);
        }
        if (state == VisitState.VISITED) {
            return;
        }

        states.put(code, VisitState.VISITING);
        String parentCode = categories.get(code).parentCode();
        if (parentCode != null) {
            visit(parentCode, categories, states);
        }
        states.put(code, VisitState.VISITED);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private TourApiCategorySnapshotException invalid(String message) {
        return new TourApiCategorySnapshotException(message);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
