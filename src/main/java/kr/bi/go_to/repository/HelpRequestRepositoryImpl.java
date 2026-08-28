package kr.bi.go_to.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.bi.go_to.model.help.HelpRequest;
import kr.bi.go_to.model.help.HelpRequestStatus;
import kr.bi.go_to.model.help.QHelpRequest;
import kr.bi.go_to.model.help.QHelpRequestRejection;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public class HelpRequestRepositoryImpl implements HelpRequestRepositoryCustom {

    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public HelpRequestRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<HelpRequest> findNearbyOpenRequests(
            Long memberId,
            HelpRequestStatus status,
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusMeters,
            Instant now) {
        QHelpRequest request = QHelpRequest.helpRequest;
        Point currentLocation = point(latitude, longitude);
        NumberExpression<Double> distanceMeters = distanceMeters(request, currentLocation);

        return queryFactory
                .selectFrom(request)
                .where(
                        request.status.eq(status),
                        request.expiresAt.gt(now),
                        request.requester.id.ne(memberId),
                        notRejectedBy(request, memberId),
                        withinBoundingBox(request, latitude, longitude, radiusMeters),
                        distanceMeters.loe((double) radiusMeters))
                .orderBy(request.requestedAt.desc())
                .fetch();
    }

    @Override
    public long countPendingRequests(Long memberId, Instant now) {
        QHelpRequest request = QHelpRequest.helpRequest;
        Long count = queryFactory
                .select(request.count())
                .from(request)
                .where(
                        request.status.eq(HelpRequestStatus.REQUESTED),
                        request.expiresAt.gt(now),
                        request.requester.id.ne(memberId),
                        notRejectedBy(request, memberId))
                .fetchOne();
        return count == null ? 0L : count;
    }

    @Override
    public Optional<HelpRequest> findByIdForUpdate(UUID id) {
        QHelpRequest request = QHelpRequest.helpRequest;
        return Optional.ofNullable(queryFactory
                .selectFrom(request)
                .where(request.id.eq(id))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne());
    }

    @Override
    public int expireRequestedRequests(
            HelpRequestStatus requestedStatus, HelpRequestStatus expiredStatus, Instant now) {
        QHelpRequest request = QHelpRequest.helpRequest;
        entityManager.flush();
        long updated = queryFactory
                .update(request)
                .set(request.status, expiredStatus)
                .where(request.status.eq(requestedStatus), request.expiresAt.loe(now))
                .execute();
        entityManager.clear();
        return Math.toIntExact(updated);
    }

    private BooleanExpression notRejectedBy(QHelpRequest request, Long memberId) {
        QHelpRequestRejection rejection = QHelpRequestRejection.helpRequestRejection;
        return JPAExpressions.selectOne()
                .from(rejection)
                .where(rejection.helpRequest.eq(request), rejection.member.id.eq(memberId))
                .notExists();
    }

    private BooleanExpression withinBoundingBox(
            QHelpRequest request, BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        double latitudeValue = latitude.doubleValue();
        double latitudeDelta = radiusMeters / METERS_PER_LATITUDE_DEGREE;
        double longitudeScale = Math.max(Math.abs(Math.cos(Math.toRadians(latitudeValue))), 0.01);
        double longitudeDelta = radiusMeters / (METERS_PER_LATITUDE_DEGREE * longitudeScale);

        return request.latitude
                .between(
                        BigDecimal.valueOf(latitudeValue - latitudeDelta),
                        BigDecimal.valueOf(latitudeValue + latitudeDelta))
                .and(request.longitude.between(
                        BigDecimal.valueOf(longitude.doubleValue() - longitudeDelta),
                        BigDecimal.valueOf(longitude.doubleValue() + longitudeDelta)));
    }

    private Point point(BigDecimal latitude, BigDecimal longitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude.doubleValue(), latitude.doubleValue()));
    }

    private NumberExpression<Double> distanceMeters(QHelpRequest request, Point currentLocation) {
        SimpleExpression<Point> requestLocation = Expressions.simpleTemplate(
                Point.class,
                "function('ST_SetSRID', function('ST_MakePoint', {0}, {1}), 4326)",
                request.longitude,
                request.latitude);
        return Expressions.numberTemplate(
                Double.class, "function('ST_DistanceSphere', {0}, {1})", requestLocation, currentLocation);
    }
}
