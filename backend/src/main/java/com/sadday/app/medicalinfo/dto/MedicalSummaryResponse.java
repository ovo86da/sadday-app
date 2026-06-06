package com.sadday.app.medicalinfo.dto;

import java.util.UUID;

/** Vista reducida para Jefe de Salida: solo datos relevantes para emergencias. */
public record MedicalSummaryResponse(
        UUID    socioId,
        String  nombreCompleto,
        String  bloodType,
        boolean hasRelevantAllergies,
        String  allergiesDetail,
        boolean usesEmergencyMedication,
        String  emergencyMedicationDetail
) {}
