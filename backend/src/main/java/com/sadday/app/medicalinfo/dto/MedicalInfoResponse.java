package com.sadday.app.medicalinfo.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MedicalInfoResponse(
        UUID          id,
        String        bloodType,
        boolean       hasRelevantAllergies,
        String        allergiesDetail,
        boolean       hasRelevantMedicalCondition,
        String        medicalConditionDetail,
        boolean       usesEmergencyMedication,
        String        emergencyMedicationDetail,
        String        additionalNotes,
        LocalDateTime updatedAt
) {}
