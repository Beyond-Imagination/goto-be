package kr.bi.go_to.model.help;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.common.UuidV7;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "help_requests")
public class HelpRequest extends BaseAuditEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helper_id")
    private Member helper;

    @Column(nullable = false, length = 255)
    private String locationLabel;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    private Integer floorLevel;

    @Column(length = 500)
    private String message;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "help_request_kinds", joinColumns = @JoinColumn(name = "help_request_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private Set<HelpKind> kinds = EnumSet.noneOf(HelpKind.class);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HelpRequestStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant acceptedAt;

    private Instant completedAt;

    private Instant canceledAt;

    public HelpRequest(
            Place place,
            Member requester,
            String locationLabel,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer floorLevel,
            String message,
            Set<HelpKind> kinds,
            Instant requestedAt,
            Instant expiresAt) {
        this.id = UuidV7.generate();
        this.place = place;
        this.requester = requester;
        this.locationLabel = locationLabel;
        this.latitude = latitude;
        this.longitude = longitude;
        this.floorLevel = floorLevel;
        this.message = message;
        this.kinds = kinds == null || kinds.isEmpty() ? EnumSet.noneOf(HelpKind.class) : EnumSet.copyOf(kinds);
        this.status = HelpRequestStatus.REQUESTED;
        this.requestedAt = requestedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isRequester(Member member) {
        return requester.getId().equals(member.getId());
    }

    public boolean isHelper(Member member) {
        return helper != null && helper.getId().equals(member.getId());
    }

    public boolean isExpired(Instant now) {
        return status == HelpRequestStatus.REQUESTED && !expiresAt.isAfter(now);
    }

    public void expire(Instant now) {
        if (isExpired(now)) {
            status = HelpRequestStatus.EXPIRED;
        }
    }

    public void accept(Member helper, Instant now) {
        this.helper = helper;
        status = HelpRequestStatus.ACCEPTED;
        acceptedAt = now;
    }

    /** 도우미가 수락을 무르면 요청은 다시 열린 상태로 돌아간다. */
    public void cancelAccept() {
        this.helper = null;
        this.acceptedAt = null;
        this.status = HelpRequestStatus.REQUESTED;
    }

    public void complete(Instant now) {
        status = HelpRequestStatus.COMPLETED;
        completedAt = now;
    }

    public void cancel(Instant now) {
        status = HelpRequestStatus.CANCELED;
        canceledAt = now;
    }
}
