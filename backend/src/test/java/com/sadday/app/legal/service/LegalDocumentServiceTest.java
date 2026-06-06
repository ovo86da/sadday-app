package com.sadday.app.legal.service;

import com.sadday.app.legal.dto.CreateLegalDocumentRequest;
import com.sadday.app.legal.dto.LegalDocumentResponse;
import com.sadday.app.legal.dto.NewVersionRequest;
import com.sadday.app.legal.entity.LegalDocument;
import com.sadday.app.legal.repository.LegalDocumentAcceptanceRepository;
import com.sadday.app.legal.repository.LegalDocumentRepository;
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
@DisplayName("LegalDocumentService — Unit Tests")
class LegalDocumentServiceTest {

    @Mock LegalDocumentRepository         legalDocumentRepository;
    @Mock LegalDocumentAcceptanceRepository acceptanceRepository;
    @Mock SocioRepository                 socioRepository;

    @InjectMocks LegalDocumentService service;

    private UUID actorId;
    private Socio actor;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        actor = Socio.builder().id(actorId).nombre("Admin").apellido("Test").build();
    }

    // =========================================================================
    // getActiveDocument
    // =========================================================================

    @Test
    @DisplayName("getActiveDocument — código existente → devuelve documento")
    void getActiveDocument_found() {
        LegalDocument doc = buildDoc("DATA_PROCESSING_POLICY", 1, true);
        when(legalDocumentRepository.findByCodeAndActiveTrue("DATA_PROCESSING_POLICY"))
                .thenReturn(Optional.of(doc));

        LegalDocumentResponse result = service.getActiveDocument("DATA_PROCESSING_POLICY");

        assertThat(result.code()).isEqualTo("DATA_PROCESSING_POLICY");
        assertThat(result.active()).isTrue();
    }

    @Test
    @DisplayName("getActiveDocument — código inexistente → LEGAL_DOCUMENT_NOT_FOUND")
    void getActiveDocument_notFound() {
        when(legalDocumentRepository.findByCodeAndActiveTrue(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveDocument("INEXISTENTE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND);
    }

    // =========================================================================
    // createDocument
    // =========================================================================

    @Test
    @DisplayName("createDocument — código nuevo → crea versión 1")
    void createDocument_success() {
        CreateLegalDocumentRequest req = new CreateLegalDocumentRequest(
                "NEW_DOC", "Nuevo documento", "desc", "NEW_DOC",
                "REGISTRATION", "Contenido aquí", true, true);

        when(legalDocumentRepository.existsByCodeAndVersion("NEW_DOC", 1)).thenReturn(false);
        when(socioRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(legalDocumentRepository.save(any())).thenAnswer(inv -> {
            LegalDocument d = inv.getArgument(0);
            d = LegalDocument.builder()
                    .id(UUID.randomUUID()).code(d.getCode()).title(d.getTitle())
                    .documentType(d.getDocumentType()).requiredStage(d.getRequiredStage())
                    .version(1).content(d.getContent()).contentHash(d.getContentHash())
                    .active(false).required(true).requiresReacceptanceOnNewVersion(true)
                    .build();
            return d;
        });

        LegalDocumentResponse result = service.createDocument(req, actorId);

        assertThat(result.code()).isEqualTo("NEW_DOC");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.active()).isFalse();
        assertThat(result.contentHash()).isNotBlank();
    }

    @Test
    @DisplayName("createDocument — versión 1 ya existe → LEGAL_DOCUMENT_VERSION_EXISTS")
    void createDocument_versionExists() {
        CreateLegalDocumentRequest req = new CreateLegalDocumentRequest(
                "EXISTING", "Título", null, "EXISTING", "REGISTRATION", "Contenido", true, true);
        when(legalDocumentRepository.existsByCodeAndVersion("EXISTING", 1)).thenReturn(true);

        assertThatThrownBy(() -> service.createDocument(req, actorId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LEGAL_DOCUMENT_VERSION_EXISTS);
    }

    // =========================================================================
    // createNewVersion
    // =========================================================================

    @Test
    @DisplayName("createNewVersion — documento existente → crea siguiente versión")
    void createNewVersion_success() {
        UUID existingId = UUID.randomUUID();
        LegalDocument existing = buildDoc("MY_CODE", 1, true);
        existing = LegalDocument.builder()
                .id(existingId).code("MY_CODE").title("Título").documentType("MY_CODE")
                .requiredStage("REGISTRATION").version(1).content("old content")
                .contentHash("oldhash").active(true).required(true)
                .requiresReacceptanceOnNewVersion(true).build();

        when(legalDocumentRepository.findById(existingId)).thenReturn(Optional.of(existing));
        when(socioRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(legalDocumentRepository.findMaxVersionByCode("MY_CODE")).thenReturn(Optional.of(1));
        when(legalDocumentRepository.save(any())).thenAnswer(inv -> {
            LegalDocument d = inv.getArgument(0);
            return LegalDocument.builder()
                    .id(UUID.randomUUID()).code(d.getCode()).title(d.getTitle())
                    .documentType(d.getDocumentType()).requiredStage(d.getRequiredStage())
                    .version(d.getVersion()).content(d.getContent()).contentHash(d.getContentHash())
                    .active(false).required(true).requiresReacceptanceOnNewVersion(true)
                    .build();
        });

        LegalDocumentResponse result = service.createNewVersion(existingId, new NewVersionRequest("nuevo contenido"), actorId);

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.active()).isFalse();
        assertThat(result.contentHash()).isNotBlank();
        assertThat(result.contentHash()).isNotEqualTo("oldhash");
    }

    // =========================================================================
    // activateVersion
    // =========================================================================

    @Test
    @DisplayName("activateVersion — documento existente → activa y devuelve activo")
    void activateVersion_success() {
        UUID docId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId).code("MY_CODE").title("Título MY_CODE")
                .documentType("MY_CODE").requiredStage("REGISTRATION")
                .version(2).content("Contenido").contentHash("fakehash")
                .active(false).required(true).requiresReacceptanceOnNewVersion(true)
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(socioRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(legalDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LegalDocumentResponse result = service.activateVersion(docId, actorId);

        assertThat(result.active()).isTrue();
        verify(legalDocumentRepository).deactivateOtherVersions(doc.getCode(), docId);
    }

    @Test
    @DisplayName("activateVersion — ID inexistente → LEGAL_DOCUMENT_NOT_FOUND")
    void activateVersion_notFound() {
        when(legalDocumentRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateVersion(UUID.randomUUID(), actorId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND);
    }

    // =========================================================================
    // SHA-256 — el hash debe ser determinista
    // =========================================================================

    @Test
    @DisplayName("createDocument — mismo contenido siempre produce el mismo hash")
    void createDocument_hashIsDeterministic() {
        String content = "Contenido de prueba";
        CreateLegalDocumentRequest req1 = new CreateLegalDocumentRequest(
                "DOC_A", "Título", null, "DOC_A", "REGISTRATION", content, true, true);
        CreateLegalDocumentRequest req2 = new CreateLegalDocumentRequest(
                "DOC_B", "Título", null, "DOC_B", "REGISTRATION", content, true, true);

        when(legalDocumentRepository.existsByCodeAndVersion(any(), eq(1))).thenReturn(false);
        when(socioRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(legalDocumentRepository.save(any())).thenAnswer(inv -> {
            LegalDocument d = inv.getArgument(0);
            return LegalDocument.builder().id(UUID.randomUUID())
                    .code(d.getCode()).title(d.getTitle()).documentType(d.getDocumentType())
                    .requiredStage(d.getRequiredStage()).version(1).content(d.getContent())
                    .contentHash(d.getContentHash()).active(false).required(true)
                    .requiresReacceptanceOnNewVersion(true).build();
        });

        LegalDocumentResponse r1 = service.createDocument(req1, actorId);
        LegalDocumentResponse r2 = service.createDocument(req2, actorId);

        assertThat(r1.contentHash()).isEqualTo(r2.contentHash());
        assertThat(r1.contentHash()).hasSize(64);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private LegalDocument buildDoc(String code, int version, boolean active) {
        return LegalDocument.builder()
                .id(UUID.randomUUID())
                .code(code).title("Título " + code)
                .documentType(code).requiredStage("REGISTRATION")
                .version(version).content("Contenido")
                .contentHash("fakehash").active(active)
                .required(true).requiresReacceptanceOnNewVersion(true)
                .build();
    }
}
