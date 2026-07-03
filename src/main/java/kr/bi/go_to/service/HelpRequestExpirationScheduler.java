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

    @Scheduled(fixedDelayString = "${help-request.expiration.fixed-delay:60000}")
    @Transactional
    public void expireRequestedRequests() {
        helpRequestRepository.expireRequestedRequests(
                HelpRequestStatus.REQUESTED, HelpRequestStatus.EXPIRED, Instant.now(clock));
    }
}
