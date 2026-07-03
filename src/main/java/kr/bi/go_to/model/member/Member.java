package kr.bi.go_to.model.member;

import jakarta.persistence.*;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.common.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public Member(Role role, String nickname) {
        this.role = role;
        this.nickname = nickname;
    }
}
