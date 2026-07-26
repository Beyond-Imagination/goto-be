package kr.bi.go_to.batch.category.batch;

import kr.bi.go_to.batch.category.dto.TourApiCategorySyncResult;
import kr.bi.go_to.batch.category.sync.TourApiCategorySynchronizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TourApiCategorySyncTasklet implements Tasklet {

    private final TourApiCategorySynchronizer synchronizer;

    public TourApiCategorySyncTasklet(TourApiCategorySynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        TourApiCategorySyncResult result = synchronizer.synchronize();
        log.info(
                "Tour API taxonomy synchronized. token={}, pages={}, large={}, middle={}, small={}",
                result.syncToken(),
                result.pageCount(),
                result.largeCount(),
                result.middleCount(),
                result.smallCount());
        return RepeatStatus.FINISHED;
    }
}
