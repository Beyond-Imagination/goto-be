package kr.bi.go_to.batch.category.validation;

import kr.bi.go_to.batch.category.repository.TourApiCategoryRepository;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryException;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TourApiPlaceCategoryValidator {

    private final TourApiCategoryRepository categoryRepository;

    public String requireActiveLeaf(TourApiItemDto item) {
        String categoryCode = item.lclsSystm3();
        if (!StringUtils.hasText(categoryCode)) {
            throw new InvalidTourApiCategoryException(
                    InvalidTourApiCategoryReason.MISSING_CURRENT_LEAF, item.contentid(), categoryCode);
        }

        // Repository/infrastructure exceptions intentionally propagate and fail the step.
        if (!categoryRepository.isActiveLeaf(categoryCode)) {
            throw new InvalidTourApiCategoryException(
                    InvalidTourApiCategoryReason.UNKNOWN_INACTIVE_OR_NON_LEAF, item.contentid(), categoryCode);
        }
        categoryRepository.findActiveLeafAncestry(categoryCode);
        return categoryCode;
    }
}
