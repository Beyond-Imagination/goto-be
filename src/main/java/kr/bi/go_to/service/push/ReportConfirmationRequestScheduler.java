package kr.bi.go_to.service.push;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.repository.ObstacleReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오래된 제보의 제보자에게 "지금도 그대로인가요?"를 묻는다 (내 정보 › 알림 설정 「내 제보 확인 요청」).
 *
 * <p>ObstacleReport.isStale과 같은 30일 기준을 쓴다. 같은 제보를 매일 찌르지 않도록
 * 한 번 물어본 제보는 다시 30일이 지나야 대상이 된다.
 */
@Component
public class ReportConfirmationRequestScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportConfirmationRequestScheduler.class);

    /** 한 번 물어본 뒤 다시 물어보기까지 기다리는 기간. */
    private static final Duration ASK_INTERVAL = Duration.ofDays(30);

    /** 한 번에 처리할 제보 수. 알림 폭주와 긴 트랜잭션을 막는다. */
    private static final int BATCH_LIMIT = 200;

    private final ObstacleReportRepository obstacleReportRepository;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    public ReportConfirmationRequestScheduler(
            ObstacleReportRepository obstacleReportRepository,
            PushNotificationService pushNotificationService,
            Clock clock) {
        this.obstacleReportRepository = obstacleReportRepository;
        this.pushNotificationService = pushNotificationService;
        this.clock = clock;
    }

    /** 매일 오전 10시(KST). 이른 아침이나 밤에 알림이 울리지 않게 시간을 고정한다. */
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    public void requestConfirmationForStaleReports() {
        int sent = requestConfirmations();
        if (sent > 0) {
            log.info("오래된 제보 확인 요청 푸시 {}건을 보냈습니다.", sent);
        }
    }

    /** 스케줄과 분리해 테스트에서 직접 호출한다. */
    @Transactional
    public int requestConfirmations() {
        Instant now = Instant.now(clock);
        List<ObstacleReport> stale = obstacleReportRepository.findStaleForConfirmationRequest(
                now.minus(ObstacleReport.STALE_THRESHOLD), now.minus(ASK_INTERVAL), BATCH_LIMIT);

        int sent = 0;
        for (ObstacleReport report : stale) {
            Instant reference =
                    report.getLastConfirmedAt() != null ? report.getLastConfirmedAt() : report.getCreatedAt();
            long days = Duration.between(reference, now).toDays();

            sent += pushNotificationService.send(
                    PushNotificationType.MY_REPORT_CONFIRMATION_REQUESTED,
                    List.of(report.getReporter().getId()),
                    PushMessages.myReportConfirmationRequested(report.getId(), report.getIssueType(), days));

            // 알림 설정이 꺼져 있어 실제로 안 나갔더라도 "물어본 것"으로 기록한다.
            // 그러지 않으면 이 제보가 매일 조회 대상에 다시 올라온다.
            report.markConfirmationRequested(now);
        }

        return sent;
    }
}
