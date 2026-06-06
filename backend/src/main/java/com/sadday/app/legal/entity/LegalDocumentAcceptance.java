package com.sadday.app.legal.entity;

import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro inmutable de aceptación electrónica de un documento legal.
 * IP, user-agent y hash son capturados en backend — nunca en frontend.
 * Esta tabla es append-only: nunca se hace UPDATE ni DELETE.
 */
@Entity
@Table(name = "legal_document_acceptances")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalDocumentAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_document_id", nullable = false)
    private LegalDocument legalDocument;

    @Column(name = "document_code", nullable = false, length = 50)
    private String documentCode;

    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    @Builder.Default
    private boolean accepted = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        if (acceptedAt == null) acceptedAt = LocalDateTime.now();
    }
}
