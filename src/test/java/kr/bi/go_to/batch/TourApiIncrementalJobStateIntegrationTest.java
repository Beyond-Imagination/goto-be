package kr.bi.go_to.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ResolvableType;
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
@SpringBootTest(
        properties = {"tour-api.detail-quota=10", "tour-api.category-page-size=1000", "tour-api.detail-concurrency=1"})
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TourApiIncrementalJobStateIntegrationTest.MockHttpConfig.class})
class TourApiIncrementalJobStateIntegrationTest {

    private static final String CONTENT_ID = "state-job-1";
    private static final String CATEGORY_CODE = "LEAF";

    @TestConfiguration(proxyBeanMethods = false)
    static class MockHttpConfig {

        @Bean
        MockHttpFixture mockHttpFixture(ObjectMapper objectMapper) {
            RestClient.Builder builder = RestClient.builder().configureMessageConverters(configurer -> {
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
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
                    .ignoreExpectOrder(true)
                    .build();
            return new MockHttpFixture(builder, server);
        }

        @Bean
        @Primary
        RestClient.Builder restClientBuilder(MockHttpFixture fixture) {
            return fixture.builder();
        }

        @Bean
        MockRestServiceServer mockRestServiceServer(MockHttpFixture fixture) {
            return fixture.server();
        }
    }

    record MockHttpFixture(RestClient.Builder builder, MockRestServiceServer server) {}

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private Job tourApiIncrementalSyncJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM place_bf_info");
        jdbcTemplate.update("DELETE FROM places");
        jdbcTemplate.update("DELETE FROM tour_api_categories");
        jdbcTemplate.update("DELETE FROM batch_sync_log");
        mockServer.reset();
        jobOperatorTestUtils.setJob(tourApiIncrementalSyncJob);
    }

    @Test
    @DisplayName("nonempty incremental item은 detail endpoint를 한 번씩만 호출하고 두 번째 job에서 재호출하지 않는다")
    void nonemptyIncrementalItemConvergesWithoutSameJobOrNextJobRefetch() throws Exception {
        expectTaxonomyAndIncrementalListTwice();
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailCommon2")))
                .andRespond(withSuccess(commonResponse(), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailWithTour2")))
                .andRespond(withSuccess(emptyDetailResponse(), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailIntro2")))
                .andRespond(withSuccess(emptyDetailResponse(), MediaType.APPLICATION_JSON));

        JobExecution first = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("state.test.run.id", UUID.randomUUID().toString())
                .toJobParameters());
        JobExecution second = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("state.test.run.id", UUID.randomUUID().toString())
                .toJobParameters());

        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertStepCounts(first, "tourApiIncrementalBaseSyncStep", 1, 1);
        assertStepCounts(first, "tourApiDetailSyncStep", 1, 1);
        assertStepCounts(second, "tourApiIncrementalBaseSyncStep", 1, 1);
        assertStepCounts(second, "tourApiDetailSyncStep", 0, 0);
        assertThat(jdbcTemplate.queryForMap(
                        """
                        SELECT category_resolution_status,
                               detail_common_status, detail_with_tour_status, detail_intro_status,
                               detail_common_synced, detail_with_tour_synced, detail_intro_synced
                        FROM places
                        WHERE external_id = ? AND source = 'TOUR_API'
                        """,
                        CONTENT_ID))
                .containsEntry("category_resolution_status", "RESOLVED")
                .containsEntry("detail_common_status", "SUCCESS")
                .containsEntry("detail_with_tour_status", "NOT_FOUND")
                .containsEntry("detail_intro_status", "NOT_FOUND")
                .containsEntry("detail_common_synced", true)
                .containsEntry("detail_with_tour_synced", false)
                .containsEntry("detail_intro_synced", false);
        mockServer.verify();
    }

    @Test
    @DisplayName("복구된 invalid category는 dependent endpoint 호출 없이 terminal 저장되어 다음 job에서 제외된다")
    void invalidRecoveredCategoryIsPersistedTerminalAndNotRetried() throws Exception {
        insertPendingPlace("invalid-category");
        expectTaxonomyAndEmptyIncrementalListTwice();
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailCommon2")))
                .andRespond(withSuccess(commonResponse("invalid-category", "UNKNOWN"), MediaType.APPLICATION_JSON));

        JobExecution first = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("state.test.run.id", UUID.randomUUID().toString())
                .toJobParameters());
        JobExecution second = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("state.test.run.id", UUID.randomUUID().toString())
                .toJobParameters());

        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertStepCounts(first, "tourApiDetailSyncStep", 1, 1);
        assertStepCounts(second, "tourApiDetailSyncStep", 0, 0);
        assertThat(detailState("invalid-category"))
                .containsEntry("category_resolution_status", "NOT_FOUND")
                .containsEntry("detail_common_status", "SUCCESS")
                .containsEntry("detail_with_tour_status", "SKIPPED")
                .containsEntry("detail_intro_status", "SKIPPED");
        mockServer.verify();
    }

    @Test
    @DisplayName("한 endpoint 인프라 실패는 그 endpoint만 재시도하고 terminal sibling은 재호출하지 않는다")
    void retriesOnlyTheEndpointThatRemainedPendingAfterInfrastructureFailure() throws Exception {
        insertPendingPlace("partial-failure");
        expectTaxonomyAndEmptyIncrementalListTwice();
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailCommon2")))
                .andRespond(withSuccess(commonResponse("partial-failure", CATEGORY_CODE), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailWithTour2")))
                .andRespond(withSuccess(providerFailureResponse(), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailWithTour2")))
                .andRespond(withSuccess(withTourResponse("partial-failure"), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailIntro2")))
                .andRespond(withSuccess(introResponse("partial-failure"), MediaType.APPLICATION_JSON));

        JobExecution first = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("state.test.run.id", UUID.randomUUID().toString())
                .toJobParameters());
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(detailState("partial-failure"))
                .containsEntry("detail_common_status", "SUCCESS")
                .containsEntry("detail_with_tour_status", "PENDING")
                .containsEntry("detail_intro_status", "SUCCESS");

        JobExecution second = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("state.test.run.id", UUID.randomUUID().toString())
                .toJobParameters());

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertStepCounts(first, "tourApiDetailSyncStep", 1, 1);
        assertStepCounts(second, "tourApiDetailSyncStep", 1, 1);
        assertThat(detailState("partial-failure"))
                .containsEntry("detail_common_status", "SUCCESS")
                .containsEntry("detail_with_tour_status", "SUCCESS")
                .containsEntry("detail_intro_status", "SUCCESS");
        assertThat(jdbcTemplate.queryForMap(
                        """
                        SELECT bf.bf_details #>> '{sources,tour_api,detailWithTour,parking}' AS parking,
                               bf.bf_details #>> '{sources,tour_api,detailIntro,usetime}' AS usetime
                        FROM place_bf_info bf
                        JOIN places p ON p.id = bf.place_id
                        WHERE p.external_id = ? AND p.source = 'TOUR_API'
                        """,
                        "partial-failure"))
                .containsEntry("parking", "전용 주차")
                .containsEntry("usetime", "09:00");
        mockServer.verify();
    }

    @Test
    @DisplayName("withTour 성공 후 intro 재시도도 저장된 원문을 병합해 place_bf_info를 완성한다")
    void combinesWithTourPayloadWithIntroFromTheNextJob() throws Exception {
        insertPendingPlace("opposite-partial");
        expectTaxonomyAndEmptyIncrementalListTwice();
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailCommon2")))
                .andRespond(withSuccess(commonResponse("opposite-partial", CATEGORY_CODE), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailWithTour2")))
                .andRespond(withSuccess(withTourResponse("opposite-partial"), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailIntro2")))
                .andRespond(withSuccess(providerFailureResponse(), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.once(), requestTo(containsString("/detailIntro2")))
                .andRespond(withSuccess(introResponse("opposite-partial"), MediaType.APPLICATION_JSON));

        JobExecution first = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("state.test.run.id", UUID.randomUUID().toString())
                .toJobParameters());
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(detailState("opposite-partial"))
                .containsEntry("detail_with_tour_status", "SUCCESS")
                .containsEntry("detail_intro_status", "PENDING");

        JobExecution second = jobOperatorTestUtils.startJob(new JobParametersBuilder()
                .addString("state.test.run.id", UUID.randomUUID().toString())
                .toJobParameters());

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(detailState("opposite-partial"))
                .containsEntry("detail_with_tour_status", "SUCCESS")
                .containsEntry("detail_intro_status", "SUCCESS");
        assertThat(jdbcTemplate.queryForMap(
                        """
                        SELECT bf.bf_details #>> '{sources,tour_api,detailWithTour,parking}' AS parking,
                               bf.bf_details #>> '{sources,tour_api,detailIntro,usetime}' AS usetime
                        FROM place_bf_info bf
                        JOIN places p ON p.id = bf.place_id
                        WHERE p.external_id = ? AND p.source = 'TOUR_API'
                        """,
                        "opposite-partial"))
                .containsEntry("parking", "전용 주차")
                .containsEntry("usetime", "09:00");
        mockServer.verify();
    }

    private void assertStepCounts(JobExecution execution, String stepName, long readCount, long writeCount) {
        assertThat(execution.getStepExecutions())
                .filteredOn(step -> stepName.equals(step.getStepName()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.getReadCount()).isEqualTo(readCount);
                    assertThat(step.getWriteCount()).isEqualTo(writeCount);
                });
    }

    private void expectTaxonomyAndIncrementalListTwice() {
        mockServer
                .expect(ExpectedCount.twice(), requestTo(containsString("/lclsSystmCode2")))
                .andRespond(withSuccess(taxonomyResponse(), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.twice(), requestTo(containsString("/areaBasedSyncList2")))
                .andRespond(withSuccess(incrementalResponse(), MediaType.APPLICATION_JSON));
    }

    private void expectTaxonomyAndEmptyIncrementalListTwice() {
        mockServer
                .expect(ExpectedCount.twice(), requestTo(containsString("/lclsSystmCode2")))
                .andRespond(withSuccess(taxonomyResponse(), MediaType.APPLICATION_JSON));
        mockServer
                .expect(ExpectedCount.twice(), requestTo(containsString("/areaBasedSyncList2")))
                .andRespond(withSuccess(emptyIncrementalResponse(), MediaType.APPLICATION_JSON));
    }

    private void insertPendingPlace(String externalId) {
        jdbcTemplate.update(
                """
                INSERT INTO places (external_id, source, name, content_type_id)
                VALUES (?, 'TOUR_API', ?, '12')
                """,
                externalId,
                externalId);
    }

    private Map<String, Object> detailState(String externalId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT category_resolution_status,
                       detail_common_status, detail_with_tour_status, detail_intro_status
                FROM places
                WHERE external_id = ? AND source = 'TOUR_API'
                """,
                externalId);
    }

    private String taxonomyResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "numOfRows": 1000,
                      "pageNo": 1,
                      "totalCount": 1,
                      "items": {
                        "item": [{
                          "lclsSystm1Cd": "LARGE",
                          "lclsSystm1Nm": "대분류",
                          "lclsSystm2Cd": "MIDDLE",
                          "lclsSystm2Nm": "중분류",
                          "lclsSystm3Cd": "LEAF",
                          "lclsSystm3Nm": "소분류"
                        }]
                      }
                    }
                  }
                }
                """;
    }

    private String incrementalResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "numOfRows": 1000,
                      "pageNo": 1,
                      "totalCount": 1,
                      "items": {
                        "item": [{
                          "contentid": "%s",
                          "contenttypeid": "12",
                          "title": "상태 수렴 장소",
                          "addr1": "서울",
                          "mapx": "127.0",
                          "mapy": "37.0",
                          "lclsSystm1": "LARGE",
                          "lclsSystm2": "MIDDLE",
                          "lclsSystm3": "%s",
                          "showflag": "1"
                        }]
                      }
                    }
                  }
                }
                """
                .formatted(CONTENT_ID, CATEGORY_CODE);
    }

    private String emptyIncrementalResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "numOfRows": 1000,
                      "pageNo": 1,
                      "totalCount": 0,
                      "items": {"item": []}
                    }
                  }
                }
                """;
    }

    private String commonResponse() {
        return commonResponse(CONTENT_ID, null);
    }

    private String commonResponse(String contentId, String categoryCode) {
        String categoryField = categoryCode == null ? "" : ", \"lclsSystm3\": \"" + categoryCode + "\"";
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": {
                        "item": [{
                          "contentid": "%s",
                          "overview": "설명",
                          "homepage": ""%s
                        }]
                      }
                    }
                  }
                }
                """
                .formatted(contentId, categoryField);
    }

    private String emptyDetailResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {"items": {"item": []}}
                  }
                }
                """;
    }

    private String withTourResponse(String contentId) {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": {
                        "item": [{
                          "contentid": "%s",
                          "parking": "전용 주차"
                        }]
                      }
                    }
                  }
                }
                """
                .formatted(contentId);
    }

    private String introResponse(String contentId) {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": {
                        "item": [{
                          "contentid": "%s",
                          "usetime": "09:00"
                        }]
                      }
                    }
                  }
                }
                """
                .formatted(contentId);
    }

    private String providerFailureResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "9999", "resultMsg": "temporary provider failure"},
                    "body": {"items": {"item": []}}
                  }
                }
                """;
    }
}
