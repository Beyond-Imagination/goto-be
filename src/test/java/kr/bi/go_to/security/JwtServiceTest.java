package kr.bi.go_to.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import kr.bi.go_to.config.security.JwtClaims;
import kr.bi.go_to.service.JwtService;
import kr.bi.go_to.enums.TokenType;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JwtServiceTest {

    @Autowired
    JwtService jwtService;

    @Test
    void createsAndValidatesAccessToken() {
        String token = jwtService.createAccessToken("tester");

        assertThat(jwtService.parseAndValidate(token, TokenType.ACCESS))
                .isPresent()
                .get()
                .extracting(JwtClaims::subject)
                .isEqualTo("tester");
    }

    @Test
    void rejectsRefreshTokenWhenAccessTokenIsExpected() {
        String token = jwtService.createRefreshToken("tester", UUID.randomUUID());

        assertThat(jwtService.parseAndValidate(token, TokenType.ACCESS)).isEmpty();
    }
}
