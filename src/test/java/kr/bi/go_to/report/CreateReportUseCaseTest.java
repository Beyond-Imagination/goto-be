package kr.bi.go_to.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.bi.go_to.controller.report.request.CreateReportRequest;
import kr.bi.go_to.controller.report.response.ReportResponse;
import kr.bi.go_to.service.report.mock.MockReportService;
import kr.bi.go_to.usecase.CreateReportUseCase;
import org.junit.jupiter.api.Test;

class CreateReportUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-12T04:30:10Z"), ZoneOffset.UTC);

    private final CreateReportUseCase useCase = new CreateReportUseCase(new MockReportService(CLOCK));

    @Test
    void returnsCalibrationWhenReportedNodeIsCheckpoint() {
        ReportResponse response = useCase.execute(10L, new CreateReportRequest(1L, "BROKEN", " stopped "));

        assertThat(response.nodeId()).isEqualTo(1L);
        assertThat(response.description()).isEqualTo("stopped");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-12T04:30:10Z"));
        assertThat(response.calibration()).isNotNull();
        assertThat(response.calibration().confirmedAt()).isEqualTo(response.createdAt());
        assertThat(response.calibration().latitude()).isEqualTo(37.523850);
        assertThat(response.calibration().longitude()).isEqualTo(126.980470);
        assertThat(response.calibration().snapRadius()).isEqualTo(5);
    }

    @Test
    void returnsNullCalibrationWhenReportedNodeIsNotCheckpoint() {
        ReportResponse response = useCase.execute(10L, new CreateReportRequest(3L, "BLOCKED", null));

        assertThat(response.nodeId()).isEqualTo(3L);
        assertThat(response.calibration()).isNull();
    }
}
