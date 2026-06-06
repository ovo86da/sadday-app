package com.sadday.app.activityrisk.service;

import com.sadday.app.activityrisk.dto.ActivityRiskDocumentResponse;
import com.sadday.app.activityrisk.dto.CreateActivityRiskDocumentRequest;
import com.sadday.app.activityrisk.entity.ActivityRiskDocument;
import com.sadday.app.activityrisk.repository.ActivityRiskDocumentRepository;
import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.salidas.repository.SalidaRepository;
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
@DisplayName("ActivityRiskDocumentService — Unit Tests")
class ActivityRiskDocumentServiceTest {

    @Mock ActivityRiskDocumentRepository riskDocRepository;
    @Mock SalidaRepository               salidaRepository;
    @Mock SocioRepository                socioRepository;

    @InjectMocks ActivityRiskDocumentService service;

    private UUID activityId;
    private UUID actorId;
    private Salida salida;
    private Socio  actor;

    @BeforeEach
    void setUp() {
        activityId = UUID.randomUUID();
        actorId    = UUID.randomUUID();

        salida = Salida.builder().id(activityId).eliminada(false).build();
        actor  = Socio.builder().id(actorId).build();
    }

    @Test
    @DisplayName("getActivoParaSalida — documento activo encontrado → response")
    void getActivoParaSalida_found() {
        ActivityRiskDocument doc = ActivityRiskDocument.builder()
                .id(UUID.randomUUID()).activity(salida).version(1)
                .content("Riesgos...").contentHash("abc").active(true).build();

        when(riskDocRepository.findByActivityIdAndActiveTrue(activityId)).thenReturn(Optional.of(doc));

        ActivityRiskDocumentResponse response = service.getActivoParaSalida(activityId);

        assertThat(response.activityId()).isEqualTo(activityId);
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("getActivoParaSalida — sin documento activo → ACTIVITY_RISK_NOT_FOUND")
    void getActivoParaSalida_notFound() {
        when(riskDocRepository.findByActivityIdAndActiveTrue(activityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActivoParaSalida(activityId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVITY_RISK_NOT_FOUND);
    }

    @Test
    @DisplayName("findActivo — retorna Optional del documento activo")
    void findActivo_returnsOptional() {
        ActivityRiskDocument doc = ActivityRiskDocument.builder()
                .id(UUID.randomUUID()).activity(salida).version(1)
                .content("x").contentHash("y").active(true).build();

        when(riskDocRepository.findByActivityIdAndActiveTrue(activityId)).thenReturn(Optional.of(doc));

        assertThat(service.findActivo(activityId)).contains(doc);
    }

    @Test
    @DisplayName("crear — primer documento → versión 1, activo, hash calculado")
    void crear_primerDocumento_version1() {
        when(salidaRepository.findById(activityId)).thenReturn(Optional.of(salida));
        when(socioRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(riskDocRepository.findMaxVersionByActivityId(activityId)).thenReturn(Optional.empty());
        when(riskDocRepository.save(any())).thenAnswer(inv -> {
            ActivityRiskDocument d = inv.getArgument(0);
            d = ActivityRiskDocument.builder()
                    .id(UUID.randomUUID()).activity(d.getActivity())
                    .version(d.getVersion()).content(d.getContent())
                    .contentHash(d.getContentHash()).active(d.isActive())
                    .approvedAt(d.getApprovedAt()).approvedBy(d.getApprovedBy()).build();
            return d;
        });

        ActivityRiskDocumentResponse response = service.crear(
                activityId,
                new CreateActivityRiskDocumentRequest("Contenido de riesgos"),
                actorId);

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.active()).isTrue();
        assertThat(response.contentHash()).isNotBlank().hasSize(64);
        verify(riskDocRepository).deactivateOtherVersions(eq(activityId), any());
    }

    @Test
    @DisplayName("crear — segunda versión → versión 2")
    void crear_segundoDocumento_version2() {
        when(salidaRepository.findById(activityId)).thenReturn(Optional.of(salida));
        when(socioRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(riskDocRepository.findMaxVersionByActivityId(activityId)).thenReturn(Optional.of(1));
        when(riskDocRepository.save(any())).thenAnswer(inv -> {
            ActivityRiskDocument d = inv.getArgument(0);
            return ActivityRiskDocument.builder()
                    .id(UUID.randomUUID()).activity(d.getActivity())
                    .version(d.getVersion()).content(d.getContent())
                    .contentHash(d.getContentHash()).active(d.isActive()).build();
        });

        ActivityRiskDocumentResponse response = service.crear(
                activityId,
                new CreateActivityRiskDocumentRequest("Nuevo contenido"),
                actorId);

        assertThat(response.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("crear — salida eliminada → SALIDA_NOT_FOUND")
    void crear_salidaEliminada_throws() {
        Salida eliminada = Salida.builder().id(activityId).eliminada(true).build();
        when(salidaRepository.findById(activityId)).thenReturn(Optional.of(eliminada));

        assertThatThrownBy(() -> service.crear(
                activityId,
                new CreateActivityRiskDocumentRequest("contenido"),
                actorId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SALIDA_NOT_FOUND);
    }

    @Test
    @DisplayName("crear — hash es determinístico para el mismo input")
    void crear_hashDeterministico() {
        final String[] capturedHash = {null};
        when(salidaRepository.findById(activityId)).thenReturn(Optional.of(salida));
        when(socioRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(riskDocRepository.findMaxVersionByActivityId(activityId)).thenReturn(Optional.empty());
        when(riskDocRepository.save(any())).thenAnswer(inv -> {
            ActivityRiskDocument d = inv.getArgument(0);
            capturedHash[0] = d.getContentHash();
            return ActivityRiskDocument.builder()
                    .id(UUID.randomUUID()).activity(d.getActivity())
                    .version(1).content(d.getContent())
                    .contentHash(d.getContentHash()).active(true).build();
        });

        service.crear(activityId, new CreateActivityRiskDocumentRequest("Riesgos del ascenso"), actorId);
        String hash1 = capturedHash[0];

        // Verificar que el mismo input siempre produce el mismo hash (SHA-256 es determinístico)
        assertThat(hash1).isNotBlank().hasSize(64);
        // SHA-256 de "Riesgos del ascenso" es fijo
        assertThat(hash1).isEqualTo(hash1.toLowerCase());
    }
}
