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
                HelpRequestStatus.REQUESTED,
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

    @Test
    void 대기_중인_도움_요청_건수를_정확하게_집계한다() {
        saveRequest(requester, "첫 번째 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        saveRequest(requester, "두 번째 요청", "35.8300000", "129.2290000", NOW.minusSeconds(30), NOW.plusSeconds(300));

        long count = helpRequestRepository.countPendingRequests(helper.getId(), NOW);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void 대기_중인_도움_요청이_없으면_0을_반환한다() {
        long count = helpRequestRepository.countPendingRequests(helper.getId(), NOW);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void 만료된_도움_요청은_대기_건수_집계에서_제외한다() {
        saveRequest(requester, "만료된 요청", "35.8294371", "129.2286552", NOW.minusSeconds(600), NOW.minusSeconds(60));

        long count = helpRequestRepository.countPendingRequests(helper.getId(), NOW);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void 대기_상태가_아닌_요청은_집계에서_제외한다() {
        HelpRequest accepted = saveRequest(
                requester, "수락된 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        accepted.accept(helper, NOW.minusSeconds(30));

        HelpRequest completed = saveRequest(
                requester, "완료된 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        completed.accept(helper, NOW.minusSeconds(30));
        completed.complete(NOW.minusSeconds(10));

        HelpRequest canceled = saveRequest(
                requester, "취소된 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        canceled.cancel(NOW.minusSeconds(10));

        helpRequestRepository.saveAll(List.of(accepted, completed, canceled));

        long count = helpRequestRepository.countPendingRequests(helper.getId(), NOW);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void 본인이_생성한_도움_요청은_집계에서_제외한다() {
        saveRequest(helper, "본인 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));

        long count = helpRequestRepository.countPendingRequests(helper.getId(), NOW);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void 본인이_거절한_도움_요청은_집계에서_제외한다() {
        HelpRequest rejected = saveRequest(
                requester, "거절할 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        rejectionRepository.save(new HelpRequestRejection(rejected, helper, NOW));

        long count = helpRequestRepository.countPendingRequests(helper.getId(), NOW);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void 복합_상태에서_도움_가능한_대기_요청만_정확히_카운트한다() {
        // 1. 유효한 요청 2건 (카운트 대상)
        saveRequest(requester, "유효 요청 1", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        saveRequest(requester, "유효 요청 2", "35.8300000", "129.2290000", NOW.minusSeconds(30), NOW.plusSeconds(300));

        // 2. 만료된 요청 1건 (제외)
        saveRequest(requester, "만료 요청", "35.8294371", "129.2286552", NOW.minusSeconds(600), NOW.minusSeconds(60));

        // 3. 본인 생성 요청 1건 (제외)
        saveRequest(helper, "본인 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));

        // 4. 거절한 요청 1건 (제외)
        HelpRequest rejected = saveRequest(
                requester, "거절 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        rejectionRepository.save(new HelpRequestRejection(rejected, helper, NOW));

        // 5. 완료된 요청 1건 (제외)
        HelpRequest completed = saveRequest(
                requester, "완료 요청", "35.8294371", "129.2286552", NOW.minusSeconds(60), NOW.plusSeconds(600));
        completed.accept(requester, NOW.minusSeconds(30));
        completed.complete(NOW.minusSeconds(10));
        helpRequestRepository.save(completed);

        long count = helpRequestRepository.countPendingRequests(helper.getId(), NOW);

        assertThat(count).isEqualTo(2);
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
