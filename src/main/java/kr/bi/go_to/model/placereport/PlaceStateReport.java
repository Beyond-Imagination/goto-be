package kr.bi.go_to.model.placereport;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kr.bi.go_to.enums.PriorityFacility;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특정 장소(공원, 건물, 화장실 등)를 다녀온 사용자가 남기는 장소 단위 상태 제보 엔티티.
 *
 * <p>장애물 제보(ObstacleReport)는 좌표에 붙는 지점 제보라 Place와 연관을 갖지 않지만,
 * 이 엔티티는 반대로 좌표를 갖지 않고 Place에만 붙는다. 표시할 위치·주소는 항상 Place에서 온다.
 *
 * <p>place_bf_info(외부 동기화 데이터)를 덮어쓰지 않는다. 공식 데이터와 사용자 경험은 신선도와
 * 신뢰 근거가 달라, 사용자 제보는 별도 테이블에 시간순 이벤트로 쌓고 화면에서 함께 보여준다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "place_state_reports")
public class PlaceStateReport extends BaseAuditEntity {

    /**
     * 장소 상태 제보 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 제보 대상 장소 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /**
     * 제보를 작성한 회원 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    /**
     * 장소 전반의 이용 난이도
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false, length = 50)
    private PlaceAccessStatus accessStatus;

    /**
     * 편의시설별 상태 (선택 입력). 응답하지 않은 항목은 키 자체가 없다.
     */
    @ElementCollection
    @CollectionTable(
            name = "place_state_report_facility_statuses",
            joinColumns = @JoinColumn(name = "place_state_report_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "facility", length = 50)
    @Enumerated(EnumType.STRING)
    @Column(name = "facility_status", nullable = false, length = 50)
    @Builder.Default
    private Map<PriorityFacility, PlaceFacilityStatus> facilityStatuses = new EnumMap<>(PriorityFacility.class);

    /**
     * 이미 호스팅된 사진 URL 목록 (업로드 API로 올린 뒤 받은 URL)
     */
    @ElementCollection
    @CollectionTable(name = "place_state_report_photo_urls", joinColumns = @JoinColumn(name = "place_state_report_id"))
    @OrderColumn(name = "photo_order")
    @Column(name = "photo_url", nullable = false, length = 2048)
    @Builder.Default
    private List<String> photoUrls = List.of();

    /**
     * 제보자가 남긴 메모 (선택 입력)
     */
    @Column(columnDefinition = "TEXT")
    private String description;
}
