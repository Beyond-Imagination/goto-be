package kr.bi.go_to.model.savedplace;

import jakarta.persistence.Column;
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
import kr.bi.go_to.model.place.Place;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 저장(즐겨찾기)한 장소 정보를 관리하는 엔티티
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "saved_places",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_saved_places_member_id_place_id",
                    columnNames = {"member_id", "place_id"}),
        })
public class SavedPlace extends BaseAuditEntity {

    /**
     * 저장 장소 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 저장한 회원 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 저장된 장소 엔티티 (N:1 관계)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /**
     * 이 장소의 상태 변경 알림을 받을지 여부.
     * 저장하면 기본으로 받고(true), 저장 화면에서 장소별로 끌 수 있다.
     * 받을 알림 종류(시설 상태 변경 / 주변 장애물)는 members.preferences의 알림 설정이 정한다.
     */
    @Column(name = "notification_enabled", nullable = false)
    @Builder.Default
    private boolean notificationEnabled = true;

    public void updateNotificationEnabled(boolean enabled) {
        this.notificationEnabled = enabled;
    }
}
