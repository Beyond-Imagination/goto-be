package kr.bi.go_to.batch;

import static org.assertj.core.api.Assertions.assertThat;

import kr.bi.go_to.batch.runner.TourApiInitialLoadRunner;
import kr.bi.go_to.batch.scheduler.TourApiBatchScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class BatchTriggerProfileTest {

    @Test
    @DisplayName("local-test 프로필에서는 배치 실행 트리거 빈을 등록하지 않는다")
    void batchTriggersAreNotRegisteredInLocalTestProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local-test");
            context.register(TourApiInitialLoadRunner.class, TourApiBatchScheduler.class);

            context.refresh();

            assertThat(context.getBeansOfType(TourApiInitialLoadRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(TourApiBatchScheduler.class)).isEmpty();
        }
    }
}
