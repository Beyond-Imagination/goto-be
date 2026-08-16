package kr.bi.go_to.batch.validation;

import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryException;
import kr.bi.go_to.batch.exception.InvalidTourApiCategoryReason;
import kr.bi.go_to.batch.repository.TourApiCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TourApiPlaceCategoryValidator {

    private final TourApiCategoryRepository categoryRepository;

    public String requireActiveLeaf(TourApiItemDto item) {
        return requireActiveLeaf(item.contentid(), item.lclsSystm3());
    }

    public String requireActiveLeaf(String contentId, String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            throw new InvalidTourApiCategoryException(
                    InvalidTourApiCategoryReason.MISSING_CURRENT_LEAF, contentId, categoryCode);
        }

        if (!categoryRepository.isActiveLeaf(categoryCode)) {
            throw new InvalidTourApiCategoryException(
                    InvalidTourApiCategoryReason.UNKNOWN_INACTIVE_OR_NON_LEAF, contentId, categoryCode);
        }
        categoryRepository.findActiveLeafAncestry(categoryCode);
        return categoryCode;
    }
}
