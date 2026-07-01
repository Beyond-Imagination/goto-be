package kr.bi.go_to.model.place;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 무장애 상세 정보(JSON)를 매핑하는 스키마 클래스.
 *
 * <p>place_bf_info.bf_details는 기본적으로 mobility, visual, hearing, infant_family, intro,
 * sources 최상위 키를 가진다. 외부 데이터로 특정 편의시설의 이용 가능 여부를 판단할 수 없으면 해당
 * BfItem의 is_available, count, details는 null로 둔다. false는 외부 데이터가 명시적으로 "이용 불가"를
 * 표현할 수 있을 때만 사용해야 한다. sources는 현재 값의 원천 원문과 동기화 시각을 보존한다.
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
     * 원천별 원문 및 동기화 메타데이터
     */
    private Map<String, SourceProvenance> sources;

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

    /**
     * bf_details 값을 만든 외부 원천의 원문/메타데이터
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceProvenance {

        /**
         * 원천 시스템의 주 식별자 (예: Tour API contentid, SSIS wfcltId)
         */
        private String externalId;

        /**
         * 원천 시스템의 보조 식별자 (예: SSIS faclInfId)
         */
        private String externalSubId;

        /**
         * SSIS evalInfo처럼 원천이 제공한 항목 목록
         */
        private List<String> evalInfo;

        /**
         * 해당 원천 데이터가 bf_details에 반영된 시각
         */
        private Instant syncedAt;

        /**
         * Tour API detailWithTour2 원본 item JSON
         */
        private Map<String, Object> detailWithTour;

        /**
         * Tour API detailIntro2 원본 item JSON
         */
        private Map<String, Object> detailIntro;
    }
}
