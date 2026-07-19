package kr.bi.go_to.controller.auth;

import jakarta.validation.Valid;
import kr.bi.go_to.controller.auth.request.LoginRequest;
import kr.bi.go_to.controller.auth.request.RefreshRequest;
import kr.bi.go_to.controller.auth.response.AccessTokenResponse;
import kr.bi.go_to.controller.auth.response.LoginResponse;
import kr.bi.go_to.spec.AuthApiSpec;
import kr.bi.go_to.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApiSpec {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.CREATED)
    // TODO: 임시 로그인 API입니다. 추후 실제 계정 인증 플로우로 교체해야 합니다.
    @Override
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    // TODO: 임시 리프레시 API입니다. 추후 토큰 로테이션과 세션 정책을 다시 정해야 합니다.
    @Override
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
}
