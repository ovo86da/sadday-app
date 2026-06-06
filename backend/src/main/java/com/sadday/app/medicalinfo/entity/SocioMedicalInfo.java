package com.sadday.app.medicalinfo.entity;

import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "socio_medical_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocioMedicalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false, unique = true)
    private Socio socio;

    @Column(name = "blood_type", length = 5)
    private String bloodType;

    @Column(name = "has_relevant_allergies", nullable = false)
    @Builder.Default
    private boolean hasRelevantAllergies = false;

    @Column(name = "allergies_detail", columnDefinition = "TEXT")
    private String allergiesDetail;

    @Column(name = "has_relevant_medical_condition", nullable = false)
    @Builder.Default
    private boolean hasRelevantMedicalCondition = false;

    @Column(name = "medical_condition_detail", columnDefinition = "TEXT")
    private String medicalConditionDetail;

    @Column(name = "uses_emergency_medication", nullable = false)
    @Builder.Default
    private boolean usesEmergencyMedication = false;

    @Column(name = "emergency_medication_detail", columnDefinition = "TEXT")
    private String emergencyMedicationDetail;

    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
