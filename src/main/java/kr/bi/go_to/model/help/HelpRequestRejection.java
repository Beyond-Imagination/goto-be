package kr.bi.go_to.model.help;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.model.member.Member;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@IdClass(HelpRequestRejectionId.class)
@Table(name = "help_request_rejections")
public class HelpRequestRejection {

    @Id
    @Column(name = "help_request_id")
    private UUID helpRequestId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "help_request_id", insertable = false, updatable = false)
    private HelpRequest helpRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", insertable = false, updatable = false)
    private Member member;

    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt;

    public HelpRequestRejection(HelpRequest helpRequest, Member member, Instant rejectedAt) {
        this.helpRequestId = helpRequest.getId();
        this.memberId = member.getId();
        this.helpRequest = helpRequest;
        this.member = member;
        this.rejectedAt = rejectedAt;
    }
}
