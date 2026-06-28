package kr.bi.go_to.model.report;

import jakarta.persistence.*;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.map.FacilityNode;
import kr.bi.go_to.model.member.Member;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실시간 편의시설 상태(고장, 수리완료 등)에 대한 사용자 제보 정보를 관리하는 엔티티
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reports")
public class Report extends BaseAuditEntity {

    /**
     * 제보 내역 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 제보 대상이 되는 편의시설 노드 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private FacilityNode node;

    /**
     * 제보를 작성한 사용자(멤버) 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    /**
     * 제보 분류 유형 (예: BROKEN, REPAIRED 등)
     */
    @Column(name = "issue_type", nullable = false, length = 50)
    private String issueType;

    /**
     * 제보 내용 및 상세 설명
     */
    @Column(columnDefinition = "TEXT")
    private String description;
}
