package kr.bi.go_to.model.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import kr.bi.go_to.model.common.BaseAuditEntity;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.util.CoordinatePrecision;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 푸시를 받을 기기 하나.
 *
 * <p>주인은 회원이지만 유일 키는 토큰이다. 같은 기기에서 다른 계정으로 로그인하면
 * FCM은 같은 토큰을 그대로 주므로, 새 행을 만들지 않고 주인만 바꿔야 이전 계정의
 * 알림이 새 사용자에게 가지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "device_tokens",
        uniqueConstraints = {@UniqueConstraint(name = "uk_device_tokens_token", columnNames = "token")})
public class DeviceToken extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    /** 마지막으로 알려진 기기 위치. 위치 권한이 없으면 null이고, 그때는 반경 발송 대상에서 빠진다. */
    @Column(name = "last_latitude")
    private Double lastLatitude;

    @Column(name = "last_longitude")
    private Double lastLongitude;

    @Column(name = "last_location_at")
    private Instant lastLocationAt;

    @Column(name = "last_registered_at", nullable = false)
    private Instant lastRegisteredAt;

    public DeviceToken(Member member, String token, DevicePlatform platform, String appVersion, Instant now) {
        this.member = member;
        this.token = token;
        this.platform = platform;
        this.appVersion = appVersion;
        this.lastRegisteredAt = now;
    }

    /** 앱이 토큰을 다시 등록했다. 주인이 바뀌었을 수 있으므로 회원도 함께 덮어쓴다. */
    public void refresh(Member member, DevicePlatform platform, String appVersion, Instant now) {
        this.member = member;
        this.platform = platform;
        this.appVersion = appVersion;
        this.lastRegisteredAt = now;
    }

    /**
     * 위치는 받은 경우에만 갱신한다. 값을 안 보냈다고 이전 위치를 지우지 않는다.
     *
     * <p>받은 좌표는 소수점 3자리(약 110m)로 줄여서 저장한다. 반경 판정(기본 300m)에는
     * 충분하고, 저장소가 유출되더라도 "누가 어느 건물 어디에 있었는지"까지는 남지 않는다.
     * 뭉개는 자리는 도움 요청 위치를 도우미에게 보여줄 때와 같은 기준이다.
     */
    public void updateLocation(Double latitude, Double longitude, Instant now) {
        if (latitude == null || longitude == null) {
            return;
        }
        this.lastLatitude = CoordinatePrecision.approximate(latitude);
        this.lastLongitude = CoordinatePrecision.approximate(longitude);
        this.lastLocationAt = now;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }
}
