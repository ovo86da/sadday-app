package com.sadday.app.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad de autenticación de un socio.
 *
 * <p>Separada de {@code socios} por principio de separación de responsabilidades
 * y seguridad: los datos de credenciales no se mezclan con datos de negocio.
 *
 * <p>Campos sensibles:
 * <ul>
 *   <li>{@code passwordHash} — Argon2id, nunca se loguea ni serializa</li>
 *   <li>{@code totpSecret}   — cifrado con AES-256-GCM en la capa de aplicación</li>
 * </ul>
 */
@Entity
@Table(name = "usuarios_auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "socio_id", nullable = false, unique = true)
    private UUID socioId;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** Hash Argon2id de la contraseña. Nunca almacenar la contraseña en claro. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** Secret TOTP cifrado con AES-256-GCM. Null si 2FA no está configurado. */
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private boolean totpEnabled = false;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private short failedAttempts = 0;

    @Column(name = "login_blocked", nullable = false)
    @Builder.Default
    private boolean loginBlocked = false;

    /** Timestamp hasta el que dura el bloqueo. Null si el bloqueo es permanente (por admin). */
    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /** Si true, el usuario debe cambiar su contraseña en el próximo login. */
    @Column(name = "password_must_change", nullable = false)
    @Builder.Default
    private boolean passwordMustChange = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
