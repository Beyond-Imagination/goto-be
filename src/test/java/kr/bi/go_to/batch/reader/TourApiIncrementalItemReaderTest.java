package kr.bi.go_to.batch.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import kr.bi.go_to.batch.support.TourApiIncrementalSyncContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TourApiIncrementalItemReaderTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(ZonedDateTime.of(2026, 6, 29, 3, 0, 0, 0, KST).toInstant(), KST);

    private static final String OK_RESPONSE =
            """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{},"numOfRows":1000,"pageNo":1,"totalCount":0}}}
            """;

    private static final String EMPTY_ITEMS_STRING_RESPONSE =
            """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
            """;

    @Test
    @DisplayName("성공한 동기화 로그가 없으면 read로 KST 기준 어제 날짜의 areaBasedSyncList2를 조회한다")
    void usesKstYesterdayWhenNoSuccessfulSyncLogExists() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(restClientBuilder).build();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TourApiIncrementalItemReader reader = createReader(restClientBuilder, jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(TourApiIncrementalSyncContext.JOB_NAME)))
                .thenThrow(new EmptyResultDataAccessException(1));

        String expectedModifiedTime =
                LocalDate.now(FIXED_CLOCK).minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        mockServer
                .expect(
                        ExpectedCount.once(),
                        requestTo(allOf(
                                containsString("/areaBasedSyncList2"),
                                not(containsString("/areaBasedSyncList1")),
                                containsString("modifiedtime=" + expectedModifiedTime),
                                not(containsString("showflag=")))))
                .andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        reader.read();

        mockServer.verify();
    }

    @Test
    @DisplayName("마지막 성공 동기화 날짜가 있으면 read 후 requestDate와 nextTargetDate를 ExecutionContext에 넣는다")
    void registersRequestDateAndNextTargetDateForBatchSyncLogWriteBack() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(restClientBuilder).build();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TourApiIncrementalItemReader reader = createReader(restClientBuilder, jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(TourApiIncrementalSyncContext.JOB_NAME)))
                .thenReturn("20260628");

        JobExecution jobExecution =
                new JobExecution(1L, new JobInstance(1L, TourApiIncrementalSyncContext.JOB_NAME), new JobParameters());
        reader.beforeStep(new StepExecution(1L, TourApiIncrementalSyncContext.BASE_STEP_NAME, jobExecution));

        mockServer
                .expect(
                        ExpectedCount.once(),
                        requestTo(allOf(
                                containsString("/areaBasedSyncList2"),
                                not(containsString("/areaBasedSyncList1")),
                                containsString("modifiedtime=20260628"),
                                not(containsString("showflag=")))))
                .andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        reader.read();

        assertThat(jobExecution.getExecutionContext().getString(TourApiIncrementalSyncContext.REQUEST_DATE_KEY))
                .isEqualTo("20260628");
        assertThat(jobExecution.getExecutionContext().getString(TourApiIncrementalSyncContext.TARGET_DATE_KEY))
                .isEqualTo("20260629");
        mockServer.verify();
    }

    @Test
    @DisplayName("areaBasedSyncList2 요청 URL에 매뉴얼 v4.3 필수/선택 파라미터가 모두 포함된다")
    void includesAllRequiredAndOptionalQueryParams() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(restClientBuilder).build();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TourApiIncrementalItemReader reader = createReader(restClientBuilder, jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(TourApiIncrementalSyncContext.JOB_NAME)))
                .thenReturn("20260628");

        mockServer
                .expect(
                        ExpectedCount.once(),
                        requestTo(allOf(
                                containsString("/areaBasedSyncList2"),
                                containsString("serviceKey=test-service-key"),
                                containsString("pageNo=1"),
                                containsString("numOfRows=1000"),
                                containsString("MobileOS=ETC"),
                                containsString("MobileApp=AppTest"),
                                containsString("modifiedtime=20260628"),
                                containsString("_type=json"),
                                not(containsString("showflag=")))))
                .andRespond(withSuccess(OK_RESPONSE, MediaType.APPLICATION_JSON));

        reader.read();

        mockServer.verify();
    }

    @Test
    @DisplayName("items가 빈 문자열인 0건 응답도 역직렬화 예외 없이 read를 종료한다")
    void toleratesEmptyStringItemsWhenTotalCountIsZero() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(restClientBuilder).build();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TourApiIncrementalItemReader reader = createReader(restClientBuilder, jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(TourApiIncrementalSyncContext.JOB_NAME)))
                .thenReturn("20260628");

        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/areaBasedSyncList2")))
                .andRespond(withSuccess(EMPTY_ITEMS_STRING_RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(reader.read()).isNull();

        mockServer.verify();
    }

    @Test
    @DisplayName("resultCode가 0000이 아니면 IllegalStateException을 던진다")
    void throwsWhenResultCodeIsNotOk() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(restClientBuilder).build();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TourApiIncrementalItemReader reader = createReader(restClientBuilder, jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(TourApiIncrementalSyncContext.JOB_NAME)))
                .thenReturn("20260628");

        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/areaBasedSyncList2")))
                .andRespond(withSuccess(
                        """
                        {"response":{"header":{"resultCode":"0003","resultMsg":"인증키가 유효하지 않습니다."},"body":{"items":{},"numOfRows":1000,"pageNo":1,"totalCount":0}}}
                        """,
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(reader::read)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resultCode=0003")
                .hasMessageContaining("인증키가 유효하지 않습니다.");

        mockServer.verify();
    }

    private TourApiIncrementalItemReader createReader(RestClient.Builder restClientBuilder, JdbcTemplate jdbcTemplate) {
        TourApiIncrementalItemReader reader = new TourApiIncrementalItemReader(restClientBuilder, jdbcTemplate);
        ReflectionTestUtils.setField(reader, "serviceKey", "test-service-key");
        ReflectionTestUtils.setField(reader, "baseUrl", "https://tour-api.example");
        ReflectionTestUtils.setField(reader, "mobileOs", "ETC");
        ReflectionTestUtils.setField(reader, "mobileApp", "AppTest");
        ReflectionTestUtils.setField(reader, "clock", FIXED_CLOCK);
        return reader;
    }
}
