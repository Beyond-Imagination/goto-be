package kr.bi.go_to.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
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
    public List<ObstacleReportConfirmation> findMineWithReport(Long memberId) {
        QObstacleReportConfirmation confirmation = QObstacleReportConfirmation.obstacleReportConfirmation;
        QObstacleReport report = QObstacleReport.obstacleReport;

        return queryFactory
                .selectFrom(confirmation)
                .join(confirmation.obstacleReport, report)
                .fetchJoin()
                .where(confirmation.member.id.eq(memberId))
                .orderBy(confirmation.createdAt.desc())
                .fetch();
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
