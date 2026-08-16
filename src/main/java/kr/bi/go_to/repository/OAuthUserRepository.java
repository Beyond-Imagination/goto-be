package kr.bi.go_to.repository;

import java.util.Optional;
import kr.bi.go_to.enums.OAuthProvider;
import kr.bi.go_to.model.member.OAuthUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthUserRepository extends JpaRepository<OAuthUser, Long> {

    @EntityGraph(attributePaths = "member")
    Optional<OAuthUser> findByProviderAndProviderId(OAuthProvider provider, String providerId);
}
