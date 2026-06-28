package kr.bi.go_to.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.bi.go_to.enums.PlaceSource;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("local-test")
@Disabled("Manual E2E Test - Requires Local Test Database (docker-compose-test.yml) Running")
public class TourApiBatchManualE2ETest {

    @TestConfiguration
    public static class TestConfig {
        @Bean
        @Primary
        public RestClient.Builder restClientBuilder(ObjectMapper objectMapper) {
            return RestClient.builder().configureMessageConverters(configurer -> {
                configurer.registerDefaults();
                configurer.withJsonConverter(new JacksonJsonHttpMessageConverter((JsonMapper) objectMapper) {
                    @Override
                    public Object read(ResolvableType type, HttpInputMessage inputMessage, Map<String, Object> hints)
                            throws IOException, HttpMessageNotReadableException {
                        Class<?> rawClass = type.toClass();
                        if (rawClass != null && JsonNode.class.isAssignableFrom(rawClass)) {
                            return objectMapper.readTree(inputMessage.getBody());
                        }
                        return super.read(type, inputMessage, hints);
                    }
                });
            });
        }
    }

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private Job tourApiSyncJob;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockRestServiceServer mockServer;

    private String readResource(ClassPathResource resource) throws IOException {
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() throws IOException {
        ClassPathResource areaBasedListResource = new ClassPathResource("mock-data/areaBasedList2.json");

        if (areaBasedListResource.exists()) {
            System.out.println(">>> Mock data files found. MockRestServiceServer is enabled for E2E Test.");
            mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

            // areaBasedList2 API 모킹
            mockServer
                    .expect(ExpectedCount.once(), requestTo(containsString("/areaBasedList2")))
                    .andRespond(withSuccess(readResource(areaBasedListResource), MediaType.APPLICATION_JSON));

            // detailCommon2 API 모킹 (상세 정보)
            ClassPathResource detailCommonResource = new ClassPathResource("mock-data/detailCommon2.json");
            if (detailCommonResource.exists()) {
                mockServer
                        .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailCommon2")))
                        .andRespond(withSuccess(readResource(detailCommonResource), MediaType.APPLICATION_JSON));
            } else {
                mockServer
                        .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailCommon2")))
                        .andRespond(withSuccess(
                                "{\"response\":{\"body\":{\"items\":{\"item\":[{\"overview\":\"Mock Overview\",\"homepage\":\"Mock Homepage\"}]}}}}",
                                MediaType.APPLICATION_JSON));
            }

            // detailWithTour2 API 모킹 (무장애 여행 정보)
            ClassPathResource detailWithTourResource = new ClassPathResource("mock-data/detailWithTour2.json");
            if (detailWithTourResource.exists()) {
                mockServer
                        .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailWithTour2")))
                        .andRespond(withSuccess(readResource(detailWithTourResource), MediaType.APPLICATION_JSON));
            } else {
                mockServer
                        .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailWithTour2")))
                        .andRespond(withSuccess(
                                "{\"response\":{\"body\":{\"items\":{\"item\":[{\"contentid\":\"1433504\",\"parking\":\"장애인 주차장 있음(2)_무장애 편의시설\",\"route\":\"\",\"publictransport\":\"출입구까지 턱이 없어 휠체어 접근 가능함\",\"ticketoffice\":\"\",\"promotion\":\"\",\"wheelchair\":\"\",\"exit\":\"주출입구는 턱이 없어 휠체어 접근 가능함\",\"elevator\":\"\",\"restroom\":\"\",\"auditorium\":\"\",\"room\":\"\",\"handicapetc\":\"\",\"braileblock\":\"\",\"helpdog\":\"\",\"guidehuman\":\"\",\"audioguide\":\"\",\"bigprint\":\"\",\"brailepromotion\":\"\",\"guidesystem\":\"\",\"blindhandicapetc\":\"\",\"signguide\":\"\",\"videoguide\":\"\",\"hearingroom\":\"\",\"hearinghandicapetc\":\"\",\"stroller\":\"\",\"lactationroom\":\"\",\"babysparechair\":\"\",\"infantsfamilyetc\":\"\"}]}}}}",
                                MediaType.APPLICATION_JSON));
            }

            // detailIntro2 API 모킹 (소개 정보)
            ClassPathResource detailIntroResource = new ClassPathResource("mock-data/detailIntro2.json");
            if (detailIntroResource.exists()) {
                mockServer
                        .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailIntro2")))
                        .andRespond(withSuccess(readResource(detailIntroResource), MediaType.APPLICATION_JSON));
            } else {
                mockServer
                        .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailIntro2")))
                        .andRespond(withSuccess(
                                "{\"response\":{\"body\":{\"items\":{\"item\":[{\"contentid\":\"1433504\"}]}}}}",
                                MediaType.APPLICATION_JSON));
            }

        } else {
            System.out.println(">>> Mock data files NOT found. Requesting real Tour API server.");
        }
        jobOperatorTestUtils.setJob(tourApiSyncJob);
    }

    @Test
    void testTourApiSyncJob() throws Exception {
        // given
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobOperatorTestUtils.startJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        if (mockServer != null) {
            mockServer.verify();
        }

        // Verify that the place is stored in DB with correctly sanitized homepage URL
        Optional<Place> placeOpt = placeRepository.findByExternalIdAndSource("1433504", PlaceSource.TOUR_API.name());
        assertThat(placeOpt).isPresent();
        assertThat(placeOpt.get().getHomepage()).isEqualTo("https://blog.naver.com/kktm2021");
    }

    @Test
    void testTourApiSyncJob_InvalidHomepage_LogsToEtlFailureLog() throws Exception {
        if (mockServer != null) {
            mockServer.reset();

            // areaBasedList2 API 모킹 (1건 반환)
            ClassPathResource areaBasedListResource = new ClassPathResource("mock-data/areaBasedList2.json");
            mockServer
                    .expect(ExpectedCount.once(), requestTo(containsString("/areaBasedList2")))
                    .andRespond(withSuccess(readResource(areaBasedListResource), MediaType.APPLICATION_JSON));

            // detailCommon2 API 모킹 (2개 이상의 URL이 포함된 homepage 응답 반환)
            String invalidCommonResponse =
                    "{\"response\":{\"body\":{\"items\":{\"item\":[{\"contentid\":\"1433504\",\"title\":\"가경 터미널시장\",\"homepage\":\"<a href=\\\"http://url1.com\\\"></a> <a href=\\\"http://url2.com\\\"></a>\",\"overview\":\"설명\"}]}}}}";
            mockServer
                    .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailCommon2")))
                    .andRespond(withSuccess(invalidCommonResponse, MediaType.APPLICATION_JSON));

            // 그 외 API들 정상 모킹
            ClassPathResource detailWithTourResource = new ClassPathResource("mock-data/detailWithTour2.json");
            mockServer
                    .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailWithTour2")))
                    .andRespond(withSuccess(readResource(detailWithTourResource), MediaType.APPLICATION_JSON));

            ClassPathResource detailIntroResource = new ClassPathResource("mock-data/detailIntro2.json");
            mockServer
                    .expect(ExpectedCount.manyTimes(), requestTo(containsString("/detailIntro2")))
                    .andRespond(withSuccess(readResource(detailIntroResource), MediaType.APPLICATION_JSON));
        }

        // 테스트 수행 전에 기존의 etl_failure_log 테이블을 비웁니다.
        jdbcTemplate.update("DELETE FROM etl_failure_log");

        // given
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobOperatorTestUtils.startJob(jobParameters);

        // then
        // ItemProcessor에서 예외를 던져서 Skip이 되었으므로, Job은 COMPLETED 상태로 끝나야 합니다.
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        if (mockServer != null) {
            mockServer.verify();
        }

        // etl_failure_log 테이블에 1433504 식별자와 함께 에러 내용이 잘 들어갔는지 검증합니다.
        List<Map<String, Object>> logs =
                jdbcTemplate.queryForList("SELECT * FROM etl_failure_log WHERE external_id = '1433504'");
        assertThat(logs).hasSize(1);
        String errorMessage = (String) logs.get(0).get("error_message");
        assertThat(errorMessage).contains("Multiple URLs found in homepage");
    }
}
