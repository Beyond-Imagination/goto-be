package kr.bi.go_to.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.model.refreshToken.RefreshToken;
import kr.bi.go_to.model.refreshToken.RefreshTokenRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ClockConfig.class, JpaAuditConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Test
    void storesRefreshTokenInPostgresContainer() {
        UUID tokenId = UUID.randomUUID();
        RefreshToken saved = refreshTokenRepository.save(
                new RefreshToken(tokenId, "tester", Instant.now().plusSeconds(600)));

        assertThat(refreshTokenRepository.findById(saved.getId()))
                .isPresent()
                .get()
                .extracting(RefreshToken::getUsername)
                .isEqualTo("tester");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
