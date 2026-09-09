package kr.bi.go_to.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
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
    public List<Report> findMineWithNodeAndPlace(Long memberId) {
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
                .where(report.reporter.id.eq(memberId))
                .orderBy(report.createdAt.desc(), report.id.desc())
                .fetch();
    }
}
