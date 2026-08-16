package kr.bi.go_to.model.terms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import kr.bi.go_to.model.common.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "user_term_agreements",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_user_term_agreement_version",
                        columnNames = {"user_id", "term_key", "agreed_version"}))
public class UserTermAgreement extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "term_key", nullable = false, length = 50)
    private String termKey;

    @Column(name = "agreed_version", nullable = false, length = 20)
    private String agreedVersion;

    @Column(name = "is_agreed", nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    public UserTermAgreement(
            Long userId,
            String termKey,
            String agreedVersion,
            boolean agreed,
            Instant agreedAt,
            String clientIp,
            String userAgent) {
        this.userId = userId;
        this.termKey = termKey;
        this.agreedVersion = agreedVersion;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }
}
