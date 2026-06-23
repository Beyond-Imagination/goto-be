package kr.bi.go_to.model.map;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 실내 지도 도면의 GeoJSON 스키마를 정의하는 클래스
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FloorGeoJson {

    private String type = "FeatureCollection";
    private List<Feature> features;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Feature {
        private String type = "Feature";
        private Geometry geometry;
        private Map<String, Object> properties;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Geometry {
        private String type; // "Polygon", "MultiPolygon", "Point" 등
        // GeoJSON의 좌표 배열은 깊이가 다양하므로 Object 형식의 리스트 배열로 유연하게 받습니다.
        private Object coordinates;
    }
}
