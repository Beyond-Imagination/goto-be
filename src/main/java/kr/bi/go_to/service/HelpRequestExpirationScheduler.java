package kr.bi.go_to.service;

import java.time.Clock;
import java.time.Instant;
import kr.bi.go_to.model.help.HelpRequestRepository;
import kr.bi.go_to.model.help.HelpRequestStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HelpRequestExpirationScheduler {

    private final HelpRequestRepository helpRequestRepository;
    private final Clock clock;

    public HelpRequestExpirationScheduler(HelpRequestRepository helpRequestRepository, Clock clock) {
        this.helpRequestRepository = helpRequestRepository;
        this.clock = clock;
    }

    // TODO 뭐 서비스가 작을땐 괜찮은데 1분안에 처리가 안될정도로 많아질때는 비동기화를 하던거 그래야할듯
    @Scheduled(cron = "0 * * * * *") // 매분 0초
    @Transactional
    public void expireRequestedRequests() {
        helpRequestRepository.expireRequestedRequests(
                HelpRequestStatus.REQUESTED, HelpRequestStatus.EXPIRED, Instant.now(clock));
    }
}
