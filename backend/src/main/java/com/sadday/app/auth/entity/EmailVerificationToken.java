package com.sadday.app.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Token de un solo uso para el registro inicial de un socio.
 *
 * <p>Flujo: la Secretaria crea un socio en el sistema → el sistema genera este token
 * y envía un email de invitación → el socio hace clic en el link y establece
 * su username y contraseña → la cuenta queda activada.
 *
 * <p>Expira en {@code sadday.auth.email-verification-token-expiry-hours} horas (default: 72).
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Nulo en el flujo nuevo de pre-registro.
     * Solo presente cuando se envía invitación a un socio ya existente (flujo legacy / reenvío).
     */
    @Column(name = "socio_id")
    private UUID socioId;

    /** Cédula del candidato. Solo en el flujo nuevo de pre-registro (socioId == null). */
    @Column(length = 20)
    private String cedula;

    /** Correo del candidato. Solo en el flujo nuevo de pre-registro (socioId == null). */
    @Column(length = 255)
    private String correo;

    /** Teléfono del candidato. Solo en el flujo nuevo de pre-registro (socioId == null). */
    @Column(length = 20)
    private String telefono;

    /** SHA-256 hex del token enviado por email. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }
}
