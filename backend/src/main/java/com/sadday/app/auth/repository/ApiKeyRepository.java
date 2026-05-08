package com.sadday.app.auth.repository;

import com.sadday.app.auth.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findBySocioIdOrderByCreatedAtDesc(UUID socioId);

    @Query("""
        SELECT COUNT(k) FROM ApiKey k
        WHERE k.socioId = :socioId
          AND k.revokedAt IS NULL
          AND (k.expiresAt IS NULL OR k.expiresAt > :now)
        """)
    long countActiveBySocioId(@Param("socioId") UUID socioId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :now WHERE k.id = :id")
    void updateLastUsedAt(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
