package kr.bi.go_to.repository;

import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.place.QPlace;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public class PlaceRepositoryImpl implements PlaceRepositoryCustom {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final JPAQueryFactory queryFactory;

    public PlaceRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Place> findNearbyActivePlaces(double latitude, double longitude, int radiusMeters, int limit) {
        QPlace place = QPlace.place;
        Point currentLocation = point(latitude, longitude);
        NumberExpression<Double> distanceMeters = distanceMeters(place, currentLocation);

        return queryFactory
                .selectFrom(place)
                .where(place.isDeleted.isFalse(), place.locationPoint.isNotNull(), distanceMeters.loe((double)
                        radiusMeters))
                .orderBy(distanceMeters.asc(), place.id.asc())
                .limit(limit)
                .fetch();
    }

    private Point point(double latitude, double longitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    private NumberExpression<Double> distanceMeters(QPlace place, Point currentLocation) {
        return Expressions.numberTemplate(
                Double.class, "function('ST_DistanceSphere', {0}, {1})", place.locationPoint, currentLocation);
    }
}
