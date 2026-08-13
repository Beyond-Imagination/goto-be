package kr.bi.go_to.model.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.common.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 서비스 사용자(멤버) 정보를 관리하는 엔티티
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "members")
public class Member extends BaseAuditEntity {

    /**
     * 멤버 고유 식별자 (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 멤버의 권한 역할 (예: USER, ADMIN)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    /**
     * 멤버의 닉네임
     */
    @Column(nullable = false, unique = true, length = 100)
    private String nickname;

    @Column(name = "agreement_mask", nullable = false)
    private long agreementMask;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private MemberPreferences preferences;

    public Member(Role role, String nickname) {
        this(role, nickname, 15L, MemberPreferences.empty());
    }

    public Member(Role role, String nickname, long agreementMask, MemberPreferences preferences) {
        this.role = role;
        this.nickname = nickname;
        this.agreementMask = agreementMask;
        this.preferences = preferences;
    }
}
