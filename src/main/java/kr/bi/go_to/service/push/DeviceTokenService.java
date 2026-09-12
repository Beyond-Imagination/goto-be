package kr.bi.go_to.service.push;

import java.time.Clock;
import java.time.Instant;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.push.DevicePlatform;
import kr.bi.go_to.model.push.DeviceToken;
import kr.bi.go_to.repository.DeviceTokenRepository;
import kr.bi.go_to.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기기 토큰 등록·해제. 앱이 토큰을 받을 때마다(로그인·토큰 갱신·앱 시작) 같은 API를 호출한다. */
@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final MemberService memberService;
    private final Clock clock;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository, MemberService memberService, Clock clock) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.memberService = memberService;
        this.clock = clock;
    }

    /**
     * 토큰을 등록하거나 갱신한다(멱등).
     *
     * <p>같은 기기를 다른 계정으로 쓰면 FCM 토큰은 그대로이므로, 새로 만들지 않고 주인을 바꾼다.
     * 그래야 이전 사용자의 알림이 새 사용자 기기로 가지 않는다.
     */
    @Transactional
    public void register(
            Long memberId,
            String token,
            DevicePlatform platform,
            String appVersion,
            Double latitude,
            Double longitude) {
        Member member = memberService.getUser(memberId);
        Instant now = Instant.now(clock);

        DeviceToken deviceToken = deviceTokenRepository
                .findByToken(token)
                .map(existing -> {
                    existing.refresh(member, platform, appVersion, now);
                    return existing;
                })
                .orElseGet(() -> deviceTokenRepository.save(new DeviceToken(member, token, platform, appVersion, now)));

        deviceToken.updateLocation(latitude, longitude, now);
    }

    /**
     * 기기 위치만 갱신한다. 「주변 도움 요청」 반경 계산에 쓴다.
     * 내 토큰이 아니면 조용히 무시한다 — 남의 토큰 위치를 바꿀 수 있으면 안 된다.
     */
    @Transactional
    public void updateLocation(Long memberId, String token, double latitude, double longitude) {
        deviceTokenRepository
                .findByToken(token)
                .filter(deviceToken -> deviceToken.isOwnedBy(memberId))
                .ifPresent(deviceToken -> deviceToken.updateLocation(latitude, longitude, Instant.now(clock)));
    }

    /**
     * 로그아웃 등으로 이 기기에서 더 이상 알림을 받지 않는다.
     * 남의 토큰은 지우지 못하게 주인을 확인한다.
     */
    @Transactional
    public void unregister(Long memberId, String token) {
        deviceTokenRepository
                .findByToken(token)
                .filter(deviceToken -> deviceToken.isOwnedBy(memberId))
                .ifPresent(deviceTokenRepository::delete);
    }
}
