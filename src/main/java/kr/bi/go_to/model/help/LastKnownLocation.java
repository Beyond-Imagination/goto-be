package kr.bi.go_to.model.help;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 도움 요청 당시의 사용자 위치 스냅샷(JSON)을 매핑하는 스키마 클래스
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LastKnownLocation {

    /**
     * 실내 층수
     */
    private Integer floorLevel;

    /**
     * 위도 (Latitude)
     */
    private Double lat;

    /**
     * 경도 (Longitude)
     */
    private Double lng;

    /**
     * 위치 정확도 반경 (미터 단위)
     */
    private Double accuracy;

    /**
     * 위치 제공자 (예: GPS, PDR_ENGINE, NETWORK 등)
     */
    private LocationProvider provider;

    /**
     * 그 외 명시되지 않은 동적 메타데이터 보관
     */
    private Map<String, Object> extraInfo = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtraInfo() {
        return extraInfo;
    }

    @JsonAnySetter
    public void setExtraInfo(String key, Object value) {
        this.extraInfo.put(key, value);
    }
}
