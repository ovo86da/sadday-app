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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocioMedicalInfoService — Unit Tests")
class SocioMedicalInfoServiceTest {

    @Mock SocioMedicalInfoRepository         medicalInfoRepository;
    @Mock SocioRepository                    socioRepository;
    @Mock LegalDocumentAcceptanceRepository  acceptanceRepository;

    @InjectMocks SocioMedicalInfoService service;

    private UUID   socioId;
    private Socio  socio;

    @BeforeEach
    void setUp() {
        socioId = UUID.randomUUID();
        socio = Socio.builder().id(socioId).nombre("Test").apellido("Socio").build();
    }

    // =========================================================================
    // getMiInfo
    // =========================================================================

    @Test
    @DisplayName("getMiInfo — sin registro → respuesta vacía")
    void getMiInfo_noRecord_returnsEmpty() {
        when(medicalInfoRepository.findBySocioId(socioId)).thenReturn(Optional.empty());

        MedicalInfoResponse result = service.getMiInfo(socioId);

        assertThat(result.id()).isNull();
        assertThat(result.bloodType()).isNull();
        assertThat(result.hasRelevantAllergies()).isFalse();
    }

    @Test
    @DisplayName("getMiInfo — con registro → devuelve datos")
    void getMiInfo_withRecord_returnsData() {
        SocioMedicalInfo info = buildInfo("A+", true, "Penicilina");
        when(medicalInfoRepository.findBySocioId(socioId)).thenReturn(Optional.of(info));

        MedicalInfoResponse result = service.getMiInfo(socioId);

        assertThat(result.bloodType()).isEqualTo("A+");
        assertThat(result.hasRelevantAllergies()).isTrue();
        assertThat(result.allergiesDetail()).isEqualTo("Penicilina");
    }

    // =========================================================================
    // updateMiInfo
    // =========================================================================

    @Test
    @DisplayName("updateMiInfo — sin consentimiento → MEDICAL_DATA_CONSENT_REQUIRED")
    void updateMiInfo_noConsent_throws() {
        when(acceptanceRepository.hasAnyAcceptance(socioId, "MEDICAL_DATA_CONSENT")).thenReturn(false);

        UpdateMedicalInfoRequest req = new UpdateMedicalInfoRequest("O+", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateMiInfo(socioId, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEDICAL_DATA_CONSENT_REQUIRED);
    }

    @Test
    @DisplayName("updateMiInfo — con consentimiento, nuevo registro → crea y retorna")
    void updateMiInfo_withConsent_createsRecord() {
        when(acceptanceRepository.hasAnyAcceptance(socioId, "MEDICAL_DATA_CONSENT")).thenReturn(true);
        when(medicalInfoRepository.findBySocioId(socioId)).thenReturn(Optional.empty());
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
        when(medicalInfoRepository.save(any())).thenAnswer(inv -> {
            SocioMedicalInfo s = inv.getArgument(0);
            s = SocioMedicalInfo.builder()
                    .id(UUID.randomUUID()).socio(socio)
                    .bloodType(s.getBloodType())
                    .hasRelevantAllergies(s.isHasRelevantAllergies())
                    .build();
            return s;
        });

        UpdateMedicalInfoRequest req = new UpdateMedicalInfoRequest("B+", true, "Polen", null, null, null, null, null);
        MedicalInfoResponse result = service.updateMiInfo(socioId, req);

        assertThat(result.bloodType()).isEqualTo("B+");
        assertThat(result.hasRelevantAllergies()).isTrue();
        verify(medicalInfoRepository).save(any());
    }

    @Test
    @DisplayName("updateMiInfo — con consentimiento, actualiza existente → retorna actualizado")
    void updateMiInfo_withConsent_updatesExisting() {
        SocioMedicalInfo existing = buildInfo("O+", false, null);
        when(acceptanceRepository.hasAnyAcceptance(socioId, "MEDICAL_DATA_CONSENT")).thenReturn(true);
        when(medicalInfoRepository.findBySocioId(socioId)).thenReturn(Optional.of(existing));
        when(medicalInfoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateMedicalInfoRequest req = new UpdateMedicalInfoRequest(null, true, "Latex", null, null, null, null, null);
        MedicalInfoResponse result = service.updateMiInfo(socioId, req);

        assertThat(result.hasRelevantAllergies()).isTrue();
        assertThat(result.allergiesDetail()).isEqualTo("Latex");
        assertThat(result.bloodType()).isEqualTo("O+"); // sin cambio
    }

    // =========================================================================
    // getBySocioId (admin)
    // =========================================================================

    @Test
    @DisplayName("getBySocioId — socio inexistente → SOCIO_NOT_FOUND")
    void getBySocioId_notFound_throws() {
        when(socioRepository.existsById(socioId)).thenReturn(false);

        assertThatThrownBy(() -> service.getBySocioId(socioId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIO_NOT_FOUND);
    }

    // =========================================================================
    // getMedicalSummary
    // =========================================================================

    @Test
    @DisplayName("getMedicalSummary — sin registro → summary con nulls")
    void getMedicalSummary_noRecord_returnsNullFields() {
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
        when(medicalInfoRepository.findBySocioId(socioId)).thenReturn(Optional.empty());

        MedicalSummaryResponse result = service.getMedicalSummary(socioId);

        assertThat(result.socioId()).isEqualTo(socioId);
        assertThat(result.bloodType()).isNull();
        assertThat(result.hasRelevantAllergies()).isFalse();
    }

    @Test
    @DisplayName("getMedicalSummary — con registro → devuelve campos de emergencia")
    void getMedicalSummary_withRecord_returnsSummary() {
        SocioMedicalInfo info = buildInfo("AB+", true, "Aspirina");
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
        when(medicalInfoRepository.findBySocioId(socioId)).thenReturn(Optional.of(info));

        MedicalSummaryResponse result = service.getMedicalSummary(socioId);

        assertThat(result.bloodType()).isEqualTo("AB+");
        assertThat(result.hasRelevantAllergies()).isTrue();
        assertThat(result.allergiesDetail()).isEqualTo("Aspirina");
    }

    @Test
    @DisplayName("getMedicalSummary — socio inexistente → SOCIO_NOT_FOUND")
    void getMedicalSummary_socioNotFound_throws() {
        when(socioRepository.findById(socioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMedicalSummary(socioId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIO_NOT_FOUND);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private SocioMedicalInfo buildInfo(String bloodType, boolean allergies, String allergiesDetail) {
        return SocioMedicalInfo.builder()
                .id(UUID.randomUUID()).socio(socio)
                .bloodType(bloodType)
                .hasRelevantAllergies(allergies)
                .allergiesDetail(allergiesDetail)
                .build();
    }
}
