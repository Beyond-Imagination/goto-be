package kr.bi.go_to.model.obstaclereport;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.member.Member;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 특정 ObstacleReport를 보고 "아직 있어요"를 눌러 남기는 확인 기록.
 * 존재 자체가 확인 기록이며 별도 상태값은 없다. (member_id, obstacle_report_id) 유니크 제약으로 회원당 1회만 카운트된다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "obstacle_report_confirmations",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_obstacle_report_confirmations_report_id_member_id",
                    columnNames = {"obstacle_report_id", "member_id"}),
        })
public class ObstacleReportConfirmation extends BaseAuditEntity {

    /**
     * 확인 기록 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 확인 대상 장애물 제보 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obstacle_report_id", nullable = false)
    private ObstacleReport obstacleReport;

    /**
     * 확인한 회원 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
}
