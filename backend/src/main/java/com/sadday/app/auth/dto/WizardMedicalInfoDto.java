package com.sadday.app.auth.dto;

import jakarta.validation.constraints.Size;

public record WizardMedicalInfoDto(
        @Size(max = 5) String bloodType,
        Boolean hasRelevantAllergies,
        String allergiesDetail,
        Boolean hasRelevantMedicalCondition,
        String medicalConditionDetail,
        Boolean usesEmergencyMedication,
        String emergencyMedicationDetail,
        String additionalNotes
) {}
