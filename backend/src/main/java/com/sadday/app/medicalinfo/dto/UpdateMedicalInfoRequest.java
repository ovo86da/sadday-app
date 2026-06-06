package com.sadday.app.medicalinfo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMedicalInfoRequest(

        @Pattern(regexp = "^(A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-)?$", message = "Tipo de sangre inválido")
        String bloodType,

        Boolean hasRelevantAllergies,

        @Size(max = 2000)
        String allergiesDetail,

        Boolean hasRelevantMedicalCondition,

        @Size(max = 2000)
        String medicalConditionDetail,

        Boolean usesEmergencyMedication,

        @Size(max = 2000)
        String emergencyMedicationDetail,

        @Size(max = 2000)
        String additionalNotes
) {}
