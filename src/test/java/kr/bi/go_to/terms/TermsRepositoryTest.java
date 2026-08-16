package kr.bi.go_to.terms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.bi.go_to.model.terms.Term;
import kr.bi.go_to.model.terms.TermHistory;
import kr.bi.go_to.repository.TermHistoryRepository;
import kr.bi.go_to.repository.TermRepository;
import kr.bi.go_to.repository.UserTermAgreementRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class TermsRepositoryTest {

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private TermHistoryRepository termHistoryRepository;

    @Autowired
    private UserTermAgreementRepository userTermAgreementRepository;

    @Test
    @DisplayName("초기 시드된 약관 5종이 정상 조회된다")
    void verifyInitialTermsSeed() {
        List<Term> terms = termRepository.findAllByActiveTrueOrderByBitmaskAsc();
        assertThat(terms).hasSize(5);

        Term ageTerm = terms.get(0);
        assertThat(ageTerm.getTermKey()).isEqualTo("age");
        assertThat(ageTerm.getBitmask()).isEqualTo(1);
        assertThat(ageTerm.isRequired()).isTrue();
        assertThat(ageTerm.getSections()).isNotEmpty();

        Term termsTerm = terms.get(1);
        assertThat(termsTerm.getTermKey()).isEqualTo("terms");
        assertThat(termsTerm.getBitmask()).isEqualTo(2);

        Term privacyTerm = terms.get(2);
        assertThat(privacyTerm.getTermKey()).isEqualTo("privacy");
        assertThat(privacyTerm.getBitmask()).isEqualTo(4);

        Term locationTerm = terms.get(3);
        assertThat(locationTerm.getTermKey()).isEqualTo("location");
        assertThat(locationTerm.getBitmask()).isEqualTo(8);

        Term marketingTerm = terms.get(4);
        assertThat(marketingTerm.getTermKey()).isEqualTo("marketing");
        assertThat(marketingTerm.getBitmask()).isEqualTo(16);
        assertThat(marketingTerm.isRequired()).isFalse();
    }

    @Test
    @DisplayName("초기 시드된 약관 개정 이력이 정상 조회된다")
    void verifyInitialTermHistories() {
        Term termsTerm = termRepository.findByTermKeyAndActiveTrue("terms").orElseThrow();
        List<TermHistory> histories = termHistoryRepository.findAllByTermIdOrderByCreatedAtDesc(termsTerm.getId());

        assertThat(histories).isNotEmpty();
        assertThat(histories.get(0).getVersion()).isEqualTo("1.0.0");
        assertThat(histories.get(0).getChangeLog()).isEqualTo("최초 제정");
    }
}
