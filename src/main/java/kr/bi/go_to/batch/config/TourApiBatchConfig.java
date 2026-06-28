package kr.bi.go_to.batch.config;

import kr.bi.go_to.batch.dto.PlaceProcessingResult;
import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.listener.TourApiSkipListener;
import kr.bi.go_to.batch.processor.TourApiBaseItemProcessor;
import kr.bi.go_to.batch.processor.TourApiIncrementalItemProcessor;
import kr.bi.go_to.batch.reader.TourApiBaseItemReader;
import kr.bi.go_to.batch.reader.TourApiDetailItemReader;
import kr.bi.go_to.batch.reader.TourApiIncrementalItemReader;
import kr.bi.go_to.batch.writer.PlaceItemWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class TourApiBatchConfig {

    private final TourApiBaseItemReader baseItemReader;
    private final TourApiDetailItemReader detailItemReader;
    private final TourApiIncrementalItemReader incrementalItemReader;

    private final TourApiBaseItemProcessor baseItemProcessor;
    private final TourApiIncrementalItemProcessor incrementalItemProcessor;

    private final PlaceItemWriter itemWriter;
    private final TourApiSkipListener tourApiSkipListener;

    @Bean
    public ThreadPoolTaskExecutor tourApiDetailTaskExecutor(
            @Value("${tour-api.detail-concurrency:10}") int detailConcurrency,
            @Value("${tour-api.detail-queue-capacity:${tour-api.detail-quota:1000}}") int detailQueueCapacity,
            @Value("${tour-api.detail-await-termination-seconds:30}") int awaitTerminationSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(detailConcurrency);
        executor.setMaxPoolSize(detailConcurrency);
        executor.setQueueCapacity(detailQueueCapacity);
        executor.setThreadNamePrefix("tour-api-detail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }

    @Bean
    public Job tourApiInitialLoadJob(
            JobRepository jobRepository, Step tourApiBaseSyncStep, Step tourApiDetailSyncStep) {
        return new JobBuilder("tourApiInitialLoadJob", jobRepository)
                .start(tourApiBaseSyncStep)
                .next(tourApiDetailSyncStep)
                .build();
    }

    @Bean
    public Job tourApiIncrementalSyncJob(
            JobRepository jobRepository, Step tourApiIncrementalBaseSyncStep, Step tourApiDetailSyncStep) {
        return new JobBuilder("tourApiIncrementalSyncJob", jobRepository)
                .start(tourApiIncrementalBaseSyncStep)
                .next(tourApiDetailSyncStep)
                .build();
    }

    @Bean
    public Step tourApiBaseSyncStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("tourApiBaseSyncStep", jobRepository)
                .<TourApiItemDto, PlaceProcessingResult>chunk(100)
                .transactionManager(transactionManager)
                .reader(baseItemReader)
                .processor(baseItemProcessor)
                .writer(itemWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(100)
                .listener(tourApiSkipListener)
                .build();
    }

    @Bean
    public Step tourApiIncrementalBaseSyncStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("tourApiIncrementalBaseSyncStep", jobRepository)
                .<TourApiItemDto, PlaceProcessingResult>chunk(100)
                .transactionManager(transactionManager)
                .reader(incrementalItemReader)
                .processor(incrementalItemProcessor)
                .writer(itemWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(100)
                .listener(tourApiSkipListener)
                .build();
    }

    @Bean
    public Step tourApiDetailSyncStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("tourApiDetailSyncStep", jobRepository)
                .<TourApiItemDto, PlaceProcessingResult>chunk(100)
                .transactionManager(transactionManager)
                .reader(detailItemReader)
                .processor(baseItemProcessor)
                .writer(itemWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(100)
                .listener(tourApiSkipListener)
                .build();
    }
}
