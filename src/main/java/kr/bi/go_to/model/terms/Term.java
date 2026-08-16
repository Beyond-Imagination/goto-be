package kr.bi.go_to.model.terms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import kr.bi.go_to.model.common.BaseAuditEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "terms")
public class Term extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "term_key", nullable = false, unique = true, length = 50)
    private String termKey;

    @Column(nullable = false)
    private int bitmask;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "current_version", nullable = false, length = 20)
    private String currentVersion;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TermSection> sections;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    public Term(
            String termKey,
            int bitmask,
            String title,
            boolean required,
            String currentVersion,
            LocalDate effectiveDate,
            String summary,
            List<TermSection> sections,
            boolean active,
            String createdBy,
            String updatedBy) {
        this.termKey = termKey;
        this.bitmask = bitmask;
        this.title = title;
        this.required = required;
        this.currentVersion = currentVersion;
        this.effectiveDate = effectiveDate;
        this.summary = summary;
        this.sections = sections;
        this.active = active;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }
}
