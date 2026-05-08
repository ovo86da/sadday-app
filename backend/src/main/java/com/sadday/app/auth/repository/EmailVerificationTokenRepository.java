package com.sadday.app.auth.repository;

import com.sadday.app.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** Invalida tokens pendientes anteriores al reenviar una invitación (flujo legacy). */
    @Modifying
    @Transactional
    @Query("UPDATE EmailVerificationToken evt SET evt.used = true " +
           "WHERE evt.socioId = :socioId AND evt.used = false")
    int invalidateAllBySocioId(@Param("socioId") UUID socioId);

    /** Invalida invitaciones de pre-registro anteriores para el mismo correo (flujo nuevo). */
    @Modifying
    @Transactional
    @Query("UPDATE EmailVerificationToken evt SET evt.used = true " +
           "WHERE evt.correo = :correo AND evt.used = false AND evt.socioId IS NULL")
    int invalidateAllByCorreo(@Param("correo") String correo);

    /** Invitaciones de pre-registro pendientes (sin socio creado aún, no usadas). */
    java.util.List<EmailVerificationToken> findBySocioIdIsNullAndUsedFalseOrderByCreatedAtDesc();

    /** Elimina tokens expirados o ya usados. Llamado periódicamente por el scheduler. */
    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken evt WHERE evt.expiresAt < :now OR evt.used = true")
    int deleteExpiredAndUsed(@Param("now") java.time.LocalDateTime now);
}
