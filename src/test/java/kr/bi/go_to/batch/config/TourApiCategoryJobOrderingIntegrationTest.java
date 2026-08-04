package kr.bi.go_to.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TourApiCategoryJobOrderingIntegrationTest {

    private final Job initialJob;
    private final Job incrementalJob;

    @Autowired
    TourApiCategoryJobOrderingIntegrationTest(
            @Qualifier("tourApiInitialLoadJob") Job initialJob,
            @Qualifier("tourApiIncrementalSyncJob") Job incrementalJob) {
        this.initialJob = initialJob;
        this.incrementalJob = incrementalJob;
    }

    @Test
    @DisplayName("초기·증분 배치에서 분류 동기화와 coverage 검사가 장소 수집보다 먼저 실행된다")
    void taxonomyPublicationAndCoverageAlwaysPrecedePlaceIngestion() {
        assertThat(((SimpleJob) initialJob).getStepNames())
                .containsExactly("tourApiCategorySyncStep", "tourApiCategoryCoverageStep", "tourApiBaseSyncStep");
        assertThat(((SimpleJob) incrementalJob).getStepNames())
                .containsExactly(
                        "tourApiCategorySyncStep",
                        "tourApiCategoryCoverageStep",
                        "tourApiIncrementalBaseSyncStep",
                        "tourApiDetailSyncStep");
    }
}
