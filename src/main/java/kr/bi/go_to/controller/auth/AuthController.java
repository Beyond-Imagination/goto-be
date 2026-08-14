package kr.bi.go_to.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.bi.go_to.controller.auth.request.OAuthLoginRequest;
import kr.bi.go_to.controller.auth.request.OAuthSignupRequest;
import kr.bi.go_to.controller.auth.request.RefreshRequest;
import kr.bi.go_to.controller.auth.response.AccessTokenResponse;
import kr.bi.go_to.controller.auth.response.OAuthAuthenticationResponse;
import kr.bi.go_to.service.AuthService;
import kr.bi.go_to.spec.AuthApiSpec;
import kr.bi.go_to.util.HttpRequestUtil;
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

    @PostMapping("/oauth/login")
    @Override
    public OAuthAuthenticationResponse login(@Valid @RequestBody OAuthLoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/oauth/signup")
    @ResponseStatus(HttpStatus.OK)
    @Override
    public OAuthAuthenticationResponse signup(
            @Valid @RequestBody OAuthSignupRequest request, HttpServletRequest httpRequest) {
        String clientIp = HttpRequestUtil.getClientIp(httpRequest);
        String userAgent = HttpRequestUtil.getUserAgent(httpRequest);
        return authService.signup(request, clientIp, userAgent);
    }

    @PostMapping("/refresh")
    @Override
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
}
