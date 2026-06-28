package kr.bi.go_to.batch.reader;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TourApiIncrementalItemReaderTest {

    @Test
    void usesKstYesterdayWhenNoSuccessfulSyncLogExists() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(restClientBuilder).build();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TourApiIncrementalItemReader reader = new TourApiIncrementalItemReader(restClientBuilder, jdbcTemplate);

        ReflectionTestUtils.setField(reader, "serviceKey", "test-service-key");
        ReflectionTestUtils.setField(reader, "baseUrl", "https://tour-api.example");
        ReflectionTestUtils.setField(reader, "mobileOs", "ETC");
        ReflectionTestUtils.setField(reader, "mobileApp", "AppTest");

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        String expectedModifiedTime =
                LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        mockServer
                .expect(
                        ExpectedCount.once(),
                        requestTo(allOf(
                                containsString("/areaBasedSyncList1"),
                                containsString("modifiedtime=" + expectedModifiedTime))))
                .andRespond(withSuccess(
                        "{\"response\":{\"body\":{\"items\":{},\"numOfRows\":1000,\"pageNo\":1,\"totalCount\":0}}}",
                        MediaType.APPLICATION_JSON));

        reader.read();

        mockServer.verify();
    }
}
