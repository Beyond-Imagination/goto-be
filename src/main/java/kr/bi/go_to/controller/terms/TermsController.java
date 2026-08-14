package kr.bi.go_to.controller.terms;

import java.util.List;
import kr.bi.go_to.controller.terms.response.TermHistoryResponse;
import kr.bi.go_to.controller.terms.response.TermResponse;
import kr.bi.go_to.controller.terms.response.TermsListResponse;
import kr.bi.go_to.service.TermsService;
import kr.bi.go_to.spec.TermsApiSpec;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/terms")
public class TermsController implements TermsApiSpec {

    private final TermsService termsService;

    public TermsController(TermsService termsService) {
        this.termsService = termsService;
    }

    @GetMapping
    @Override
    public TermsListResponse getTerms() {
        return termsService.getTerms();
    }

    @GetMapping("/{termId}")
    @Override
    public TermResponse getTerm(@PathVariable String termId) {
        return termsService.getTerm(termId);
    }

    @GetMapping("/{termId}/histories")
    @Override
    public List<TermHistoryResponse> getTermHistories(@PathVariable String termId) {
        return termsService.getTermHistories(termId);
    }
}
