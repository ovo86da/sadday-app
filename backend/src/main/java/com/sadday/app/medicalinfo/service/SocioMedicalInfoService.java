package com.sadday.app.medicalinfo.service;

import com.sadday.app.legal.repository.LegalDocumentAcceptanceRepository;
import com.sadday.app.medicalinfo.dto.MedicalInfoResponse;
import com.sadday.app.medicalinfo.dto.MedicalSummaryResponse;
import com.sadday.app.medicalinfo.dto.UpdateMedicalInfoRequest;
import com.sadday.app.medicalinfo.entity.SocioMedicalInfo;
import com.sadday.app.medicalinfo.repository.SocioMedicalInfoRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SocioMedicalInfoService {

    static final String MEDICAL_CONSENT_CODE = "MEDICAL_DATA_CONSENT";

    private final SocioMedicalInfoRepository    medicalInfoRepository;
    private final SocioRepository               socioRepository;
    private final LegalDocumentAcceptanceRepository acceptanceRepository;

    // =========================================================================
    // Socio propio
    // =========================================================================

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public MedicalInfoResponse getMiInfo(UUID socioId) {
        return medicalInfoRepository.findBySocioId(socioId)
                .map(this::toResponse)
                .orElse(emptyResponse());
    }

    @PreAuthorize("isAuthenticated()")
    public MedicalInfoResponse updateMiInfo(UUID socioId, UpdateMedicalInfoRequest request) {
        if (!acceptanceRepository.hasAnyAcceptance(socioId, MEDICAL_CONSENT_CODE)) {
            throw new BusinessException(ErrorCode.MEDICAL_DATA_CONSENT_REQUIRED,
                    "Debes aceptar el Consentimiento para el Tratamiento de Datos de Salud antes de registrar información médica");
        }
        return doUpdate(socioId, request);
    }

    // =========================================================================
    // Admin / Secretaria
    // =========================================================================

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Transactional(readOnly = true)
    public MedicalInfoResponse getBySocioId(UUID socioId) {
        ensureSocioExists(socioId);
        return medicalInfoRepository.findBySocioId(socioId)
                .map(this::toResponse)
                .orElse(emptyResponse());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public MedicalInfoResponse updateBySocioId(UUID socioId, UpdateMedicalInfoRequest request) {
        ensureSocioExists(socioId);
        return doUpdate(socioId, request);
    }

    // =========================================================================
    // Resumen para Jefe de Salida / Directivo
    // =========================================================================

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Transactional(readOnly = true)
    public MedicalSummaryResponse getMedicalSummary(UUID socioId) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        return medicalInfoRepository.findBySocioId(socioId)
                .map(info -> new MedicalSummaryResponse(
                        socio.getId(),
                        socio.getNombre() + " " + socio.getApellido(),
                        info.getBloodType(),
                        info.isHasRelevantAllergies(),
                        info.getAllergiesDetail(),
                        info.isUsesEmergencyMedication(),
                        info.getEmergencyMedicationDetail()))
                .orElse(new MedicalSummaryResponse(
                        socio.getId(),
                        socio.getNombre() + " " + socio.getApellido(),
                        null, false, null, false, null));
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private MedicalInfoResponse doUpdate(UUID socioId, UpdateMedicalInfoRequest req) {
        SocioMedicalInfo info = medicalInfoRepository.findBySocioId(socioId)
                .orElseGet(() -> {
                    Socio socio = socioRepository.findById(socioId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
                    return SocioMedicalInfo.builder().socio(socio).build();
                });

        if (req.bloodType()                != null) info.setBloodType(req.bloodType());
        if (req.hasRelevantAllergies()     != null) info.setHasRelevantAllergies(req.hasRelevantAllergies());
        if (req.allergiesDetail()          != null) info.setAllergiesDetail(req.allergiesDetail());
        if (req.hasRelevantMedicalCondition() != null) info.setHasRelevantMedicalCondition(req.hasRelevantMedicalCondition());
        if (req.medicalConditionDetail()   != null) info.setMedicalConditionDetail(req.medicalConditionDetail());
        if (req.usesEmergencyMedication()  != null) info.setUsesEmergencyMedication(req.usesEmergencyMedication());
        if (req.emergencyMedicationDetail() != null) info.setEmergencyMedicationDetail(req.emergencyMedicationDetail());
        if (req.additionalNotes()          != null) info.setAdditionalNotes(req.additionalNotes());

        return toResponse(medicalInfoRepository.save(info));
    }

    private void ensureSocioExists(UUID socioId) {
        if (!socioRepository.existsById(socioId)) {
            throw new BusinessException(ErrorCode.SOCIO_NOT_FOUND);
        }
    }

    private MedicalInfoResponse toResponse(SocioMedicalInfo info) {
        return new MedicalInfoResponse(
                info.getId(),
                info.getBloodType(),
                info.isHasRelevantAllergies(),
                info.getAllergiesDetail(),
                info.isHasRelevantMedicalCondition(),
                info.getMedicalConditionDetail(),
                info.isUsesEmergencyMedication(),
                info.getEmergencyMedicationDetail(),
                info.getAdditionalNotes(),
                info.getUpdatedAt()
        );
    }

    private MedicalInfoResponse emptyResponse() {
        return new MedicalInfoResponse(null, null, false, null, false, null, false, null, null, null);
    }
}
