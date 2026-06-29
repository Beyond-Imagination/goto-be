package kr.bi.go_to.model.place;

import jakarta.persistence.*;
import kr.bi.go_to.model.common.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/**
 * 관광지 및 장소의 기본 정보를 관리하는 엔티티
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "places",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_places_external_id_source",
                    columnNames = {"external_id", "source"}),
        })
public class Place extends BaseAuditEntity {

    /**
     * 장소 고유 식별자 (내부 PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 외부 시스템(한국관광공사 등)에서의 원본 장소 ID (예: contentId)
     */
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    /**
     * 데이터 출처 (예: KNTO, SSIS, USER 등)
     */
    @Column(nullable = false, length = 50)
    private String source;

    /**
     * 장소 카테고리 (관광지, 숙박, 공공기관 등)
     */
    @Column(length = 50)
    private String category;

    /**
     * 장소명
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * 도로명/지번 등 정제된 형태의 주소
     */
    @Column(name = "sanitized_address", length = 500)
    private String sanitizedAddress;

    /**
     * 장소의 위도/경도 기반 공간 데이터(Point) 정보
     */
    @Column(name = "location_point", columnDefinition = "geometry(Point,4326)")
    private Point locationPoint;

    /**
     * 장소의 대표 썸네일 이미지 URL
     */
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /**
     * 장소에 대한 상세 설명 및 개요 텍스트
     */
    @Column(columnDefinition = "TEXT")
    private String overview;

    /**
     * 장소의 공식 홈페이지 URL
     */
    @Column(length = 1000)
    private String homepage;

    /**
     * 장소의 대표 연락처/전화번호
     */
    @Column(length = 100)
    private String tel;

    /**
     * 한국관광공사 기준 관광타입 ID (예: 12-관광지, 32-숙박 등)
     */
    @Column(name = "content_type_id", length = 50)
    private String contentTypeId;

    /**
     * 해당 장소의 삭제/비공개 여부 (Tour API 동기화 등에서 활용)
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /**
     * Tour API detailCommon2 상세 보강 성공 여부
     */
    @Column(name = "detail_common_synced", nullable = false)
    @Builder.Default
    private boolean detailCommonSynced = false;

    /**
     * Tour API detailWithTour2 상세 보강 성공 여부
     */
    @Column(name = "detail_with_tour_synced", nullable = false)
    @Builder.Default
    private boolean detailWithTourSynced = false;

    /**
     * Tour API detailIntro2 상세 보강 성공 여부
     */
    @Column(name = "detail_intro_synced", nullable = false)
    @Builder.Default
    private boolean detailIntroSynced = false;
}
