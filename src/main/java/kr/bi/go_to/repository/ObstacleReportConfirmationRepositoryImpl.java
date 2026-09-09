package kr.bi.go_to.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.model.obstaclereport.ObstacleReportConfirmation;
import kr.bi.go_to.model.obstaclereport.ObstacleReportStatus;
import kr.bi.go_to.model.obstaclereport.QObstacleReport;
import kr.bi.go_to.model.obstaclereport.QObstacleReportConfirmation;

public class ObstacleReportConfirmationRepositoryImpl implements ObstacleReportConfirmationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ObstacleReportConfirmationRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<ObstacleReportConfirmation> findMinePage(
            Long memberId, ObstacleReportStatus statusFilter, Instant afterCreatedAt, Long afterId, int limit) {
        QObstacleReportConfirmation confirmation = QObstacleReportConfirmation.obstacleReportConfirmation;
        QObstacleReport report = QObstacleReport.obstacleReport;

        return queryFactory
                .selectFrom(confirmation)
                .join(confirmation.obstacleReport, report)
                .fetchJoin()
                .where(
                        confirmation.member.id.eq(memberId),
                        statusFilter == null ? null : report.status.eq(statusFilter),
                        after(confirmation, afterCreatedAt, afterId))
                .orderBy(confirmation.createdAt.desc(), confirmation.id.desc())
                .limit(limit)
                .fetch();
    }

    /** 커서가 가리키는 항목보다 뒤(= 더 과거)에 있는 행만 남긴다. 같은 시각이면 id로 가른다. */
    private BooleanExpression after(QObstacleReportConfirmation confirmation, Instant afterCreatedAt, Long afterId) {
        if (afterCreatedAt == null || afterId == null) {
            return null;
        }
        return confirmation
                .createdAt
                .lt(afterCreatedAt)
                .or(confirmation.createdAt.eq(afterCreatedAt).and(confirmation.id.lt(afterId)));
    }

    @Override
    public long countResolvedByMember(Long memberId) {
        QObstacleReportConfirmation confirmation = QObstacleReportConfirmation.obstacleReportConfirmation;
        QObstacleReport report = QObstacleReport.obstacleReport;

        Long count = queryFactory
                .select(confirmation.count())
                .from(confirmation)
                .join(confirmation.obstacleReport, report)
                .where(confirmation.member.id.eq(memberId), report.status.eq(ObstacleReportStatus.RESOLVED))
                .fetchOne();

        return count == null ? 0L : count;
    }
}
