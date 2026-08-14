package kr.bi.go_to.service;

import java.time.Instant;
import java.util.List;
import kr.bi.go_to.controller.terms.response.TermHistoryResponse;
import kr.bi.go_to.controller.terms.response.TermResponse;
import kr.bi.go_to.controller.terms.response.TermsListResponse;
import kr.bi.go_to.exception.BusinessException;
import kr.bi.go_to.exception.ErrorCode;
import kr.bi.go_to.model.terms.Term;
import kr.bi.go_to.model.terms.UserTermAgreement;
import kr.bi.go_to.repository.TermHistoryRepository;
import kr.bi.go_to.repository.TermRepository;
import kr.bi.go_to.repository.UserTermAgreementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TermsService {

    private final TermRepository termRepository;
    private final TermHistoryRepository termHistoryRepository;
    private final UserTermAgreementRepository userTermAgreementRepository;

    public TermsService(
            TermRepository termRepository,
            TermHistoryRepository termHistoryRepository,
            UserTermAgreementRepository userTermAgreementRepository) {
        this.termRepository = termRepository;
        this.termHistoryRepository = termHistoryRepository;
        this.userTermAgreementRepository = userTermAgreementRepository;
    }

    public TermsListResponse getTerms() {
        List<TermResponse> terms = termRepository.findAllByActiveTrueOrderByBitmaskAsc().stream()
                .map(TermResponse::from)
                .toList();
        return new TermsListResponse(terms);
    }

    public TermResponse getTerm(String termKey) {
        Term term = termRepository
                .findByTermKeyAndActiveTrue(termKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERM_NOT_FOUND));
        return TermResponse.from(term);
    }

    public List<TermHistoryResponse> getTermHistories(String termKey) {
        Term term = termRepository
                .findByTermKeyAndActiveTrue(termKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERM_NOT_FOUND));
        return termHistoryRepository.findAllByTermIdOrderByCreatedAtDesc(term.getId()).stream()
                .map(TermHistoryResponse::from)
                .toList();
    }

    @Transactional
    public void recordUserAgreements(Long userId, long agreementMask, String clientIp, String userAgent) {
        List<Term> activeTerms = termRepository.findAllByActiveTrueOrderByBitmaskAsc();
        Instant now = Instant.now();

        List<UserTermAgreement> agreements = activeTerms.stream()
                .map(term -> {
                    boolean agreed = (agreementMask & term.getBitmask()) != 0;
                    return new UserTermAgreement(
                            userId, term.getTermKey(), term.getCurrentVersion(), agreed, now, clientIp, userAgent);
                })
                .toList();

        userTermAgreementRepository.saveAll(agreements);
    }

    @Transactional
    public void recordUserAgreements(Long userId, long agreementMask) {
        recordUserAgreements(userId, agreementMask, null, null);
    }
}
