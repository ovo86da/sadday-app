package com.sadday.app.auth.repository;

import com.sadday.app.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Sesiones activas de un socio, ordenadas por uso más reciente. */
    @Query("SELECT rt FROM RefreshToken rt " +
           "WHERE rt.socioId = :socioId AND rt.revoked = false AND rt.expiresAt > :now " +
           "ORDER BY COALESCE(rt.lastUsedAt, rt.createdAt) DESC")
    List<RefreshToken> findActiveBySocioId(@Param("socioId") UUID socioId,
                                           @Param("now") LocalDateTime now);

    /** Revoca la sesión activa anterior del mismo dispositivo al crear una nueva (evita acumulación). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now " +
           "WHERE rt.socioId = :socioId AND rt.deviceId = :deviceId AND rt.revoked = false")
    int revokeByDeviceId(@Param("socioId") UUID socioId,
                         @Param("deviceId") String deviceId,
                         @Param("now") LocalDateTime now);

    /**
     * Revoca sesiones sin device_id (creadas antes de FR-001) para el mismo socio.
     * Se llama junto con revokeByDeviceId() para limpiar sesiones legacy en cada login.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now " +
           "WHERE rt.socioId = :socioId AND rt.deviceId IS NULL AND rt.revoked = false")
    int revokeLegacySessionsBySocioId(@Param("socioId") UUID socioId, @Param("now") LocalDateTime now);

    /** Revoca todos los tokens activos de un socio (logout global / detección de robo). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now " +
           "WHERE rt.socioId = :socioId AND rt.revoked = false")
    int revokeAllBySocioId(@Param("socioId") UUID socioId, @Param("now") LocalDateTime now);

    /** Revoca todos los tokens activos de un socio excepto el token dado (cerrar otras sesiones). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now " +
           "WHERE rt.socioId = :socioId AND rt.id != :excludeId AND rt.revoked = false")
    int revokeAllBySocioIdExcept(@Param("socioId") UUID socioId,
                                 @Param("excludeId") UUID excludeId,
                                 @Param("now") LocalDateTime now);

    /** Limpia tokens expirados y revocados. Llamado periódicamente por el scheduler. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revoked = true")
    int deleteExpiredAndRevoked(@Param("now") LocalDateTime now);
}
