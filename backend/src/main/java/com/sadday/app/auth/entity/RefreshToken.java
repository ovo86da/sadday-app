package com.sadday.app.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Token de refresco rotativo para renovar access tokens JWT sin re-autenticar.
 *
 * <p>Modelo de seguridad:
 * <ul>
 *   <li>Solo se almacena el hash SHA-256 del token real (nunca el token en claro).</li>
 *   <li>El token real viaja únicamente en la respuesta HTTP inicial al cliente.</li>
 *   <li>Rotación: cada uso genera un nuevo token y revoca el anterior.</li>
 *   <li>Detección de robo: si un token revocado es usado de nuevo, se revocan TODOS
 *       los tokens del socio (logout global forzado).</li>
 * </ul>
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "socio_id", nullable = false)
    private UUID socioId;

    /** SHA-256 hex del token opaco. Nunca almacenar el token real. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** "WEB" o "MOBILE" — plataforma desde donde se originó la sesión. */
    @Column(name = "platform", nullable = false, length = 10)
    @Builder.Default
    private String platform = "WEB";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** SHA-256(userAgent + platform)[:32] — identifica el binomio dispositivo+browser. */
    @Column(name = "device_id", length = 32)
    private String deviceId;

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
        return !revoked && !isExpired();
    }
}
