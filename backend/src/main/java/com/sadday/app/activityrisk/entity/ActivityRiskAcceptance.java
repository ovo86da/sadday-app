package com.sadday.app.activityrisk.entity;

import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Registro inmutable de aceptación del riesgo de una actividad. Sin setters — append-only. */
@Entity
@Table(name = "activity_risk_acceptances")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRiskAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Salida activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_risk_document_id", nullable = false)
    private ActivityRiskDocument riskDocument;

    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (acceptedAt == null) acceptedAt = LocalDateTime.now();
        createdAt = LocalDateTime.now();
    }
}
