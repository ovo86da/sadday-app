package com.sadday.app.legal.entity;

import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "legal_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "required_stage", nullable = false, length = 30)
    private String requiredStage;

    @Column(nullable = false)
    private Integer version;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = true;

    @Column(name = "requires_reacceptance_on_new_version", nullable = false)
    @Builder.Default
    private boolean requiresReacceptanceOnNewVersion = true;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Socio approvedBy;

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
