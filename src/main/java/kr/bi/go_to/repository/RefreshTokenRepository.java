package kr.bi.go_to.repository;

import java.util.UUID;

import kr.bi.go_to.model.refreshToken.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {}
