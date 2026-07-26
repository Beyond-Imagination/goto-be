package kr.bi.go_to.batch.category.batch;

import java.util.List;
import kr.bi.go_to.batch.category.exception.TourApiCategorySnapshotException;
import kr.bi.go_to.batch.category.repository.TourApiCategoryRepository;
import kr.bi.go_to.batch.category.repository.TourApiCategoryRepository.TourApiCategoryCoverage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TourApiCategoryCoverageTasklet implements Tasklet {

    private final TourApiCategoryRepository repository;

    public TourApiCategoryCoverageTasklet(TourApiCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<TourApiCategoryCoverage> coverageBySource = repository.coverageBySource();
        coverageBySource.forEach(coverage -> log.info(
                "Tour API taxonomy coverage. source={}, total={}, resolved={}, null={}, blank={}, orphan={}, "
                        + "inactive={}, nonLeaf={}, brokenAncestry={}, nonTourNonNull={}",
                coverage.source(),
                coverage.total(),
                coverage.resolved(),
                coverage.nullCount(),
                coverage.blank(),
                coverage.orphan(),
                coverage.inactive(),
                coverage.nonLeaf(),
                coverage.brokenAncestry(),
                coverage.nonTourNonNull()));

        List<TourApiCategoryCoverage> blocking = coverageBySource.stream()
                .filter(TourApiCategoryCoverage::blocksIngestion)
                .toList();
        if (!blocking.isEmpty()) {
            throw new TourApiCategorySnapshotException("Tour API category coverage blocks ingestion: " + blocking);
        }
        return RepeatStatus.FINISHED;
    }
}
