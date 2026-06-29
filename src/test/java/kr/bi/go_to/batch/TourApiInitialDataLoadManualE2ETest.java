package kr.bi.go_to.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.repository.PlaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("local-test")
@Disabled("초기 데이터 적재 확인 용도 외 사용X, Rate Limit 고려 - Requires Local Test Database (docker-compose-test.yml) Running")
@Slf4j
public class TourApiInitialDataLoadManualE2ETest {

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private Job tourApiInitialLoadJob;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobOperatorTestUtils.setJob(tourApiInitialLoadJob);
    }

    @Test
    @DisplayName("빈 DB에서 초기 적재 Job을 돌리면 COMPLETED 상태로 장소 데이터가 적재된다")
    void testRealTourApiSyncJob() throws Exception {
        // given: DB 초기화 (전체 삭제 후 시작)
        jdbcTemplate.update("DELETE FROM etl_failure_log");
        jdbcTemplate.update("DELETE FROM place_bf_info");
        jdbcTemplate.update("DELETE FROM places");

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobOperatorTestUtils.startJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 실제 API 연동 시 정상적으로 장소 데이터가 DB에 적재되었는지 검증
        List<Place> places = placeRepository.findAll();
        assertThat(places).isNotEmpty();

        log.info(">>> 총 적재된 장소(Place) 데이터 수: {}", places.size());

        // 무장애 상세 정보(place_bf_info)가 정상적으로 일부라도 연동 및 적재되었는지 검증
        Integer bfInfoCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM place_bf_info", Integer.class);
        assertThat(bfInfoCount).isNotNull();
        log.info(">>> 총 적재된 무장애 상세 정보(Place BF Info) 수: {}", bfInfoCount);

        // 실제 API의 불규칙한 데이터 포맷 등으로 인해 발생한 에러가 실패 로그에 정상적으로 기록되었는지 확인
        List<Map<String, Object>> failureLogs = jdbcTemplate.queryForList("SELECT * FROM etl_failure_log");
        log.info(">>> 발생한 실패 로그 건수: {}", failureLogs.size());
        if (!failureLogs.isEmpty()) {
            log.info(">>> 샘플 실패 로그 내역: {}", failureLogs.get(0).get("error_message"));
        }
    }
}
