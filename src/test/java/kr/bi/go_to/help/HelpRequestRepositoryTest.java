package kr.bi.go_to.help;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.help.HelpRequest;
import kr.bi.go_to.model.help.HelpRequestRejection;
import kr.bi.go_to.model.help.HelpRequestStatus;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.repository.HelpRequestRejectionRepository;
import kr.bi.go_to.repository.HelpRequestRepository;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    ClockConfig.class,
    JpaAuditConfig.class,
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HelpRequestRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-03T09:00:00Z");

    @Autowired
    HelpRequestRepository helpRequestRepository;

    @Autowired
    HelpRequestRejectionRepository rejectionRepository;

    @Autowired
    MemberRepository memberRepository;

    private Member requester;
    private Member helper;

    @BeforeEach
    void setUp() {
        rejectionRepository.deleteAll();
        helpRequestRepository.deleteAll();
        memberRepository.deleteAll();

        requester = memberRepository.save(new Member(Role.USER, "requester"));
        helper = memberRepository.save(new Member(Role.USER, "helper"));
    }

    @Test
    void 주변_도움_요청_조회는_PostGIS로_반경_상태_만료_거절_본인_요청을_필터링한다() {
        HelpRequest visible = saveRequest(
                requester, "보이는 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        saveRequest(helper, "본인 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        saveRequest(requester, "반경 밖 요청", "35.9000000", "129.3000000", NOW.minusSeconds(60), NOW.plusSeconds(600));
        saveRequest(requester, "만료된 요청", "35.8294371", "129.2286552", NOW.minusSeconds(600), NOW.minusSeconds(60));
        HelpRequest rejected = saveRequest(
                requester, "거절한 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        rejectionRepository.save(new HelpRequestRejection(rejected, helper, NOW));

        List<HelpRequest> result = helpRequestRepository.findNearbyOpenRequests(
                helper.getId(),
                HelpRequestStatus.REQUESTED.name(),
                new BigDecimal("35.8294000"),
                new BigDecimal("129.2286000"),
                1_000,
                NOW);

        assertThat(result).extracting(HelpRequest::getId).containsExactly(visible.getId());
    }

    @Test
    @Transactional
    void 만료_업데이트_쿼리는_요청중이고_만료시각이_지난_도움_요청만_EXPIRED로_변경한다() {
        HelpRequest expired = saveRequest(
                requester, "만료 대상", "35.8294371", "129.2286552", NOW.minusSeconds(600), NOW.minusSeconds(60));
        HelpRequest future = saveRequest(
                requester, "아직 유효", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        HelpRequest accepted = saveRequest(
                requester, "이미 수락됨", "35.8294371", "129.2286552", NOW.minusSeconds(600), NOW.minusSeconds(60));
        accepted.accept(helper, NOW.minusSeconds(30));

        int updated = helpRequestRepository.expireRequestedRequests(
                HelpRequestStatus.REQUESTED, HelpRequestStatus.EXPIRED, NOW);

        assertThat(updated).isEqualTo(1);
        assertThat(helpRequestRepository.findById(expired.getId()))
                .isPresent()
                .get()
                .extracting(HelpRequest::getStatus)
                .isEqualTo(HelpRequestStatus.EXPIRED);
        assertThat(helpRequestRepository.findById(future.getId()))
                .isPresent()
                .get()
                .extracting(HelpRequest::getStatus)
                .isEqualTo(HelpRequestStatus.REQUESTED);
        assertThat(helpRequestRepository.findById(accepted.getId()))
                .isPresent()
                .get()
                .extracting(HelpRequest::getStatus)
                .isEqualTo(HelpRequestStatus.ACCEPTED);
    }

    private HelpRequest saveRequest(
            Member requester,
            String locationLabel,
            String latitude,
            String longitude,
            Instant requestedAt,
            Instant expiresAt) {
        return helpRequestRepository.save(new HelpRequest(
                null,
                requester,
                locationLabel,
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                null,
                null,
                requestedAt,
                expiresAt));
    }
}
