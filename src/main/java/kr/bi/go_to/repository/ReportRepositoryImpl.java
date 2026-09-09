package kr.bi.go_to.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import kr.bi.go_to.model.map.QFacilityNode;
import kr.bi.go_to.model.map.QFloorMap;
import kr.bi.go_to.model.place.QPlace;
import kr.bi.go_to.model.report.QReport;
import kr.bi.go_to.model.report.Report;

public class ReportRepositoryImpl implements ReportRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ReportRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Report> findMinePage(Long memberId, Instant afterCreatedAt, Long afterId, int limit) {
        QReport report = QReport.report;
        QFacilityNode node = QFacilityNode.facilityNode;
        QFloorMap floorMap = QFloorMap.floorMap;
        QPlace place = QPlace.place;

        return queryFactory
                .selectFrom(report)
                .join(report.node, node)
                .fetchJoin()
                .join(node.floorMap, floorMap)
                .fetchJoin()
                .join(floorMap.place, place)
                .fetchJoin()
                .where(report.reporter.id.eq(memberId), after(report, afterCreatedAt, afterId))
                .orderBy(report.createdAt.desc(), report.id.desc())
                .limit(limit)
                .fetch();
    }

    /** 커서가 가리키는 항목보다 뒤(= 더 과거)에 있는 행만 남긴다. 같은 시각이면 id로 가른다. */
    private BooleanExpression after(QReport report, Instant afterCreatedAt, Long afterId) {
        if (afterCreatedAt == null || afterId == null) {
            return null;
        }
        return report.createdAt
                .lt(afterCreatedAt)
                .or(report.createdAt.eq(afterCreatedAt).and(report.id.lt(afterId)));
    }
}
