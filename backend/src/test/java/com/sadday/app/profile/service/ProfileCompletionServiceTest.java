package com.sadday.app.profile.service;

import com.sadday.app.emergencycontacts.repository.SocioEmergencyContactRepository;
import com.sadday.app.legal.entity.LegalDocument;
import com.sadday.app.legal.repository.LegalDocumentAcceptanceRepository;
import com.sadday.app.legal.repository.LegalDocumentRepository;
import com.sadday.app.medicalinfo.repository.SocioMedicalInfoRepository;
import com.sadday.app.profile.dto.ProfileCompletionStatusResponse;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileCompletionService — Unit Tests")
class ProfileCompletionServiceTest {

    @Mock SocioRepository                    socioRepository;
    @Mock SocioEmergencyContactRepository    contactRepository;
    @Mock SocioMedicalInfoRepository         medicalInfoRepository;
    @Mock LegalDocumentRepository            documentRepository;
    @Mock LegalDocumentAcceptanceRepository  acceptanceRepository;

    @InjectMocks ProfileCompletionService service;

    private UUID   socioId;
    private Socio  socio;

    @BeforeEach
    void setUp() {
        socioId = UUID.randomUUID();
        socio = Socio.builder()
                .id(socioId).nombre("Test").apellido("Socio")
                .telefono("0991234567")
                .build();
    }

    // =========================================================================
    // socio no existe
    // =========================================================================

    @Test
    @DisplayName("getStatus — socio no existe → SOCIO_NOT_FOUND")
    void getStatus_socioNotFound_throws() {
        when(socioRepository.findById(socioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(socioId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIO_NOT_FOUND);
    }

    // =========================================================================
    // perfil completo
    // =========================================================================

    @Test
    @DisplayName("getStatus — todos los requisitos cumplidos → canEnrollActivities=true")
    void getStatus_allRequirementsMet_returnsComplete() {
        stubSocioWithTelefono();
        when(contactRepository.countBySocioId(socioId)).thenReturn(2L);
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(true);
        when(documentRepository.findActiveRequired()).thenReturn(List.of(
                buildDoc("MEDICAL_DATA_CONSENT", "Consentimiento Salud", 1),
                buildDoc("LIABILITY_WAIVER", "Descargo Responsabilidad", 1)));
        when(acceptanceRepository.findLatestAcceptedVersion(eq(socioId), anyString()))
                .thenReturn(Optional.of(1));

        ProfileCompletionStatusResponse result = service.getStatus(socioId);

        assertThat(result.canEnrollActivities()).isTrue();
        assertThat(result.profileComplete()).isTrue();
        assertThat(result.missingRequirements()).isEmpty();
        assertThat(result.pendingDocuments()).isEmpty();
        assertThat(result.expiredDocuments()).isEmpty();
    }

    // =========================================================================
    // requisitos faltantes individuales
    // =========================================================================

    @Test
    @DisplayName("getStatus — sin teléfono → missingRequirements incluye teléfono")
    void getStatus_noTelefono_includesMissing() {
        socio = Socio.builder().id(socioId).nombre("T").apellido("S").build(); // telefono null
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
        when(contactRepository.countBySocioId(socioId)).thenReturn(2L);
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(true);
        when(documentRepository.findActiveRequired()).thenReturn(List.of());

        ProfileCompletionStatusResponse result = service.getStatus(socioId);

        assertThat(result.canEnrollActivities()).isFalse();
        assertThat(result.missingRequirements()).anyMatch(r -> r.contains("teléfono"));
    }

    @Test
    @DisplayName("getStatus — solo 1 contacto → missingRequirements lo indica")
    void getStatus_oneContact_includesMissing() {
        stubSocioWithTelefono();
        when(contactRepository.countBySocioId(socioId)).thenReturn(1L);
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(true);
        when(documentRepository.findActiveRequired()).thenReturn(List.of());

        ProfileCompletionStatusResponse result = service.getStatus(socioId);

        assertThat(result.canEnrollActivities()).isFalse();
        assertThat(result.missingRequirements()).anyMatch(r -> r.contains("contacto"));
    }

    @Test
    @DisplayName("getStatus — sin información médica → missingRequirements lo indica")
    void getStatus_noMedicalInfo_includesMissing() {
        stubSocioWithTelefono();
        when(contactRepository.countBySocioId(socioId)).thenReturn(2L);
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(false);
        when(documentRepository.findActiveRequired()).thenReturn(List.of());

        ProfileCompletionStatusResponse result = service.getStatus(socioId);

        assertThat(result.canEnrollActivities()).isFalse();
        assertThat(result.missingRequirements()).anyMatch(r -> r.contains("médica"));
    }

    @Test
    @DisplayName("getStatus — documento nunca aceptado → aparece en pendingDocuments")
    void getStatus_documentNeverAccepted_appearsPending() {
        stubSocioWithTelefono();
        when(contactRepository.countBySocioId(socioId)).thenReturn(2L);
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(true);
        when(documentRepository.findActiveRequired()).thenReturn(List.of(
                buildDoc("LIABILITY_WAIVER", "Descargo", 1)));
        when(acceptanceRepository.findLatestAcceptedVersion(socioId, "LIABILITY_WAIVER"))
                .thenReturn(Optional.empty());

        ProfileCompletionStatusResponse result = service.getStatus(socioId);

        assertThat(result.canEnrollActivities()).isFalse();
        assertThat(result.pendingDocuments()).contains("LIABILITY_WAIVER");
    }

    @Test
    @DisplayName("getStatus — nueva versión sin re-aceptar → aparece en expiredDocuments")
    void getStatus_newVersionNotAccepted_appearsExpired() {
        stubSocioWithTelefono();
        when(contactRepository.countBySocioId(socioId)).thenReturn(2L);
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(true);

        LegalDocument docV2 = buildDoc("LIABILITY_WAIVER", "Descargo v2", 2);
        when(documentRepository.findActiveRequired()).thenReturn(List.of(docV2));
        // Socio aceptó la v1, ahora hay v2 activa
        when(acceptanceRepository.findLatestAcceptedVersion(socioId, "LIABILITY_WAIVER"))
                .thenReturn(Optional.of(1));

        ProfileCompletionStatusResponse result = service.getStatus(socioId);

        assertThat(result.canEnrollActivities()).isFalse();
        assertThat(result.expiredDocuments()).contains("LIABILITY_WAIVER");
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private void stubSocioWithTelefono() {
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
    }

    private LegalDocument buildDoc(String code, String title, int version) {
        LegalDocument doc = new LegalDocument();
        doc.setCode(code);
        doc.setTitle(title);
        doc.setVersion(version);
        doc.setActive(true);
        doc.setRequired(true);
        doc.setRequiresReacceptanceOnNewVersion(true);
        return doc;
    }
}
