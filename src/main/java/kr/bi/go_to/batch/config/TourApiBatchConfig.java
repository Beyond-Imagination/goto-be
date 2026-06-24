package kr.bi.go_to.batch.config;

import kr.bi.go_to.batch.dto.TourApiItemDto;
import kr.bi.go_to.batch.processor.TourApiItemProcessor;
import kr.bi.go_to.batch.reader.TourApiItemReader;
import kr.bi.go_to.batch.writer.PlaceItemWriter;
import kr.bi.go_to.model.place.Place;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class TourApiBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TourApiItemReader itemReader;
    private final TourApiItemProcessor itemProcessor;
    private final PlaceItemWriter itemWriter;

    @Bean
    public Job tourApiSyncJob() {
        return new JobBuilder("tourApiSyncJob", jobRepository)
                .start(tourApiSyncStep())
                .build();
    }

    @Bean
    public Step tourApiSyncStep() {
        return new StepBuilder("tourApiSyncStep", jobRepository)
                .<TourApiItemDto, Place>chunk(100)
                .transactionManager(transactionManager)
                .reader(itemReader)
                .processor(itemProcessor)
                .writer(itemWriter)
                .build();
    }
}
