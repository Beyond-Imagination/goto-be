package kr.bi.go_to.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.model.obstaclereport.ObstacleReport;
import kr.bi.go_to.model.obstaclereport.QObstacleReport;

public class ObstacleReportRepositoryImpl implements ObstacleReportRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ObstacleReportRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public long sumConfirmedCountByReporter(Long reporterId) {
        QObstacleReport report = QObstacleReport.obstacleReport;

        Integer sum = queryFactory
                .select(report.confirmedCount.sum())
                .from(report)
                .where(report.reporter.id.eq(reporterId))
                .fetchOne();

        // 제보가 하나도 없으면 SUM이 null이므로 0으로 내린다.
        return sum == null ? 0L : sum.longValue();
    }

    @Override
    public List<ObstacleReport> findMinePage(Long memberId, Instant afterCreatedAt, Long afterId, int limit) {
        QObstacleReport report = QObstacleReport.obstacleReport;

        return queryFactory
                .selectFrom(report)
                .where(report.reporter.id.eq(memberId), after(report, afterCreatedAt, afterId))
                .orderBy(report.createdAt.desc(), report.id.desc())
                .limit(limit)
                .fetch();
    }

    /** 커서가 가리키는 항목보다 뒤(= 더 과거)에 있는 행만 남긴다. 같은 시각이면 id로 가른다. */
    private BooleanExpression after(QObstacleReport report, Instant afterCreatedAt, Long afterId) {
        if (afterCreatedAt == null || afterId == null) {
            return null;
        }
        return report.createdAt
                .lt(afterCreatedAt)
                .or(report.createdAt.eq(afterCreatedAt).and(report.id.lt(afterId)));
    }
}
