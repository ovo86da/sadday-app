package com.sadday.app.auth.repository;

import com.sadday.app.auth.entity.MfaChallengeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MfaChallengeTokenRepository extends JpaRepository<MfaChallengeToken, UUID> {

    Optional<MfaChallengeToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM MfaChallengeToken t WHERE t.expiresAt < :now")
    int deleteExpired(LocalDateTime now);
}
