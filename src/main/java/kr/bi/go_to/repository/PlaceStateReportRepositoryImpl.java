package kr.bi.go_to.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.bi.go_to.model.place.QPlace;
import kr.bi.go_to.model.placereport.PlaceStateReport;
import kr.bi.go_to.model.placereport.QPlaceStateReport;

public class PlaceStateReportRepositoryImpl implements PlaceStateReportRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public PlaceStateReportRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<PlaceStateReport> findMinePage(Long memberId, Instant afterCreatedAt, Long afterId, int limit) {
        QPlaceStateReport report = QPlaceStateReport.placeStateReport;
        QPlace place = QPlace.place;

        return queryFactory
                .selectFrom(report)
                .join(report.place, place)
                .fetchJoin()
                .where(report.reporter.id.eq(memberId), after(report, afterCreatedAt, afterId))
                .orderBy(report.createdAt.desc(), report.id.desc())
                .limit(limit)
                .fetch();
    }

    /** 커서가 가리키는 항목보다 뒤(= 더 과거)에 있는 행만 남긴다. 같은 시각이면 id로 가른다. */
    private BooleanExpression after(QPlaceStateReport report, Instant afterCreatedAt, Long afterId) {
        if (afterCreatedAt == null || afterId == null) {
            return null;
        }
        return report.createdAt
                .lt(afterCreatedAt)
                .or(report.createdAt.eq(afterCreatedAt).and(report.id.lt(afterId)));
    }

    @Override
    public List<PlaceStateReport> findLatestByPlace(Long placeId, int limit) {
        QPlaceStateReport report = QPlaceStateReport.placeStateReport;
        QPlace place = QPlace.place;

        return queryFactory
                .selectFrom(report)
                .join(report.place, place)
                .fetchJoin()
                .where(report.place.id.eq(placeId))
                .orderBy(report.createdAt.desc(), report.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public Optional<PlaceStateReport> findByIdWithPlace(Long reportId) {
        QPlaceStateReport report = QPlaceStateReport.placeStateReport;
        QPlace place = QPlace.place;

        return Optional.ofNullable(queryFactory
                .selectFrom(report)
                .join(report.place, place)
                .fetchJoin()
                .where(report.id.eq(reportId))
                .fetchOne());
    }
}
