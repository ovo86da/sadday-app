package com.sadday.app.auth.repository;

import com.sadday.app.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Invalida todos los tokens pendientes de un socio antes de emitir uno nuevo. */
    @Modifying
    @Query("UPDATE PasswordResetToken prt SET prt.used = true " +
           "WHERE prt.socioId = :socioId AND prt.used = false")
    int invalidateAllBySocioId(@Param("socioId") UUID socioId);

    /** Cuenta solicitudes de reset emitidas para un socio desde una fecha dada (para rate limiting por correo). */
    @Query("SELECT COUNT(prt) FROM PasswordResetToken prt " +
           "WHERE prt.socioId = :socioId AND prt.createdAt >= :desde")
    long countBySocioIdAndCreatedAtAfter(@Param("socioId") UUID socioId,
                                         @Param("desde") java.time.LocalDateTime desde);

    /** Elimina tokens expirados o ya usados. Llamado periódicamente por el scheduler. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :now OR prt.used = true")
    int deleteExpiredAndUsed(@Param("now") java.time.LocalDateTime now);
}
