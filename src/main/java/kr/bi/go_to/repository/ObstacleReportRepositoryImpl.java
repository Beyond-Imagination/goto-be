package kr.bi.go_to.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
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
}
