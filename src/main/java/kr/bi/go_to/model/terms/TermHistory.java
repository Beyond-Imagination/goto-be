package kr.bi.go_to.model.terms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "term_histories",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_term_history_version",
                        columnNames = {"term_id", "version"}))
@EntityListeners(AuditingEntityListener.class)
public class TermHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TermSection> sections;

    @Column(name = "change_log", columnDefinition = "TEXT")
    private String changeLog;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    public TermHistory(
            Term term,
            String version,
            LocalDate effectiveDate,
            String summary,
            List<TermSection> sections,
            String changeLog,
            String createdBy) {
        this.term = term;
        this.version = version;
        this.effectiveDate = effectiveDate;
        this.summary = summary;
        this.sections = sections;
        this.changeLog = changeLog;
        this.createdBy = createdBy;
    }
}
