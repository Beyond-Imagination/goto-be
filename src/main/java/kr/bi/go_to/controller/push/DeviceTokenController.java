package kr.bi.go_to.controller.push;

import jakarta.validation.Valid;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.push.request.RegisterDeviceTokenRequest;
import kr.bi.go_to.controller.push.request.UpdateDeviceLocationRequest;
import kr.bi.go_to.service.push.DeviceTokenService;
import kr.bi.go_to.spec.DeviceTokenApiSpec;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 푸시를 받을 기기 토큰 등록/해제 API. */
@RestController
@RequestMapping("/api/v1/members/me/device-tokens")
public class DeviceTokenController implements DeviceTokenApiSpec {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        deviceTokenService.register(
                member.id(),
                request.token(),
                request.platform(),
                request.appVersion(),
                request.latitude(),
                request.longitude());
    }

    @Override
    @PatchMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLocation(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody UpdateDeviceLocationRequest request) {
        deviceTokenService.updateLocation(member.id(), request.token(), request.latitude(), request.longitude());
    }

    @Override
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal AuthenticatedMember member, @RequestParam String token) {
        deviceTokenService.unregister(member.id(), token);
    }
}
