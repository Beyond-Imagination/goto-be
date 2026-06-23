package kr.bi.go_to.model.place;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 무장애 상세 정보(JSON)를 매핑하는 스키마 클래스
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceBfDetails {

    /**
     * 이동약자(지체 장애인 등) 관련 편의시설 (예: 휠체어, 주차장, 경사로)
     */
    private Map<String, BfItem> mobility;

    /**
     * 시각약자 관련 편의시설 (예: 점자블록, 안내견)
     */
    private Map<String, BfItem> visual;

    /**
     * 청각약자 관련 편의시설 (예: 수어안내)
     */
    private Map<String, BfItem> hearing;

    /**
     * 영유아 동반자 관련 편의시설 (예: 수유실, 유모차 대여)
     */
    @JsonProperty("infant_family")
    private Map<String, BfItem> infantFamily;

    /**
     * 일반 소개 및 관람/이용 정보 (이용시간, 휴무일 등 비정형 데이터)
     */
    private Map<String, Object> intro;

    /**
     * 세부 편의 항목 공통 속성 정의
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BfItem {
        /**
         * 이용 가능 여부
         */
        @JsonProperty("is_available")
        private Boolean isAvailable;

        /**
         * 시설 갯수 (필요 시)
         */
        private Integer count;

        /**
         * 상세 설명 텍스트
         */
        private String details;
    }
}
