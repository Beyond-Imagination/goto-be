package kr.bi.go_to.batch.category.batch;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TourApiCategoryBatchConfig {

    @Bean
    public Step tourApiCategorySyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            TourApiCategorySyncTasklet tasklet) {
        return new StepBuilder("tourApiCategorySyncStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    public Step tourApiCategoryCoverageStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            TourApiCategoryCoverageTasklet tasklet) {
        return new StepBuilder("tourApiCategoryCoverageStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
