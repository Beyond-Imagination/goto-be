package kr.bi.go_to.model.refreshToken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.model.common.BaseAuditEntity;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseAuditEntity {

    @Id
    private UUID id;

    @Column(name = "username", nullable = false, length = 100)
    private String subject;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    protected RefreshToken() {}

    public RefreshToken(UUID id, String subject, Instant expiresAt) {
        this.id = id;
        this.subject = subject;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public UUID getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }
}
