package com.sadday.app.retirement.service;

import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.emergencycontacts.repository.SocioEmergencyContactRepository;
import com.sadday.app.medicalinfo.repository.SocioMedicalInfoRepository;
import com.sadday.app.retirement.dto.RetireSocioResponse;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.EstadoAcceso;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.EstadoAccesoRepository;
import com.sadday.app.socios.repository.EstadoCuotaRepository;
import com.sadday.app.socios.repository.SocioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocioDataRetentionService — Unit Tests")
class SocioDataRetentionServiceTest {

    @Mock SocioRepository                socioRepository;
    @Mock EstadoAccesoRepository         estadoAccesoRepository;
    @Mock EstadoCuotaRepository          cuotaRepository;
    @Mock SocioEmergencyContactRepository contactRepository;
    @Mock SocioMedicalInfoRepository      medicalInfoRepository;
    @Mock UsuarioAuthRepository           usuarioAuthRepository;
    @Mock PasswordEncoder                 passwordEncoder;

    @InjectMocks SocioDataRetentionService service;

    private UUID   socioId;
    private UUID   actorId;
    private Socio  socio;
    private EstadoAcceso estadoActive;
    private EstadoAcceso estadoExMember;

    @BeforeEach
    void setUp() {
        socioId       = UUID.randomUUID();
        actorId       = UUID.randomUUID();
        estadoActive  = EstadoAcceso.builder().id((short) 1).codigo("ACTIVE").nombre("Activo").build();
        estadoExMember = EstadoAcceso.builder().id((short) 5).codigo("EX_MEMBER").nombre("Ex Miembro").build();

        socio = Socio.builder()
                .id(socioId)
                .nombre("Juan").apellido("Pérez")
                .cedula("1234567890")
                .correo("juan@test.com")
                .telefono("0991234567")
                .direccion("Quito, Ecuador")
                .fechaIngreso(LocalDate.of(2020, 1, 1))
                .estadoAcceso(estadoActive)
                .build();
    }

    @Test
    @DisplayName("retireSocio — sin deuda → anonimiza correo, limpia teléfono y dirección")
    void retireSocio_sinDeuda_anonimiza() {
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
        when(estadoAccesoRepository.findByCodigo("EX_MEMBER")).thenReturn(Optional.of(estadoExMember));
        when(cuotaRepository.existsBySocioIdAndEstado(socioId, "PENDIENTE")).thenReturn(false);
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(true);
        when(contactRepository.countBySocioId(socioId)).thenReturn(2L);
        when(socioRepository.save(any())).thenReturn(socio);
        when(usuarioAuthRepository.findBySocioId(socioId)).thenReturn(Optional.empty());

        RetireSocioResponse response = service.retireSocio(socioId, "Solicitud del socio", actorId);

        assertThat(response.socioId()).isEqualTo(socioId);
        assertThat(response.hasPendingDebt()).isFalse();
        assertThat(socio.getEstadoAcceso().getCodigo()).isEqualTo("EX_MEMBER");
        assertThat(socio.getCorreo()).startsWith("retirado_").endsWith("@sadday.invalid");
        assertThat(socio.getTelefono()).isNull();
        assertThat(socio.getDireccion()).isNull();
        assertThat(socio.getFechaSalida()).isEqualTo(LocalDate.now());
        assertThat(response.actionsPerformed()).anyMatch(a -> a.contains("Eliminados 2 contacto"));
        assertThat(response.actionsPerformed()).anyMatch(a -> a.contains("médica eliminada"));
        assertThat(response.actionsPerformed()).anyMatch(a -> a.contains("EX_MEMBER"));
    }

    @Test
    @DisplayName("retireSocio — con deuda → conserva correo y teléfono")
    void retireSocio_conDeuda_conservaDatos() {
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
        when(estadoAccesoRepository.findByCodigo("EX_MEMBER")).thenReturn(Optional.of(estadoExMember));
        when(cuotaRepository.existsBySocioIdAndEstado(socioId, "PENDIENTE")).thenReturn(true);
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(false);
        when(contactRepository.countBySocioId(socioId)).thenReturn(0L);
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
        when(socioRepository.save(any())).thenReturn(socio);
        when(usuarioAuthRepository.findBySocioId(socioId)).thenReturn(Optional.empty());

        RetireSocioResponse response = service.retireSocio(socioId, "Baja con deuda", actorId);

        assertThat(response.hasPendingDebt()).isTrue();
        // Correo y teléfono NO modificados
        assertThat(socio.getCorreo()).isEqualTo("juan@test.com");
        assertThat(socio.getTelefono()).isEqualTo("0991234567");
        // Solo dirección limpiada
        assertThat(socio.getDireccion()).isNull();
        assertThat(response.actionsPerformed()).anyMatch(a -> a.contains("Deuda pendiente"));
    }

    @Test
    @DisplayName("retireSocio — socio no encontrado → SOCIO_NOT_FOUND")
    void retireSocio_notFound_throws() {
        when(socioRepository.findById(socioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retireSocio(socioId, "motivo", actorId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIO_NOT_FOUND);
    }

    @Test
    @DisplayName("retireSocio — ya es EX_MEMBER → SOCIO_ALREADY_RETIRED")
    void retireSocio_yaRetirado_throws() {
        socio.setEstadoAcceso(estadoExMember);
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));

        assertThatThrownBy(() -> service.retireSocio(socioId, "motivo", actorId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIO_ALREADY_RETIRED);
    }

    @Test
    @DisplayName("retireSocio — actor intenta darse de baja a sí mismo → CANNOT_RETIRE_SELF")
    void retireSocio_selfRetire_throws() {
        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));

        assertThatThrownBy(() -> service.retireSocio(socioId, "motivo", socioId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_RETIRE_SELF);
    }

    @Test
    @DisplayName("deleteEmergencyContacts — elimina y reporta en actions")
    void deleteEmergencyContacts_removesContacts() {
        when(contactRepository.countBySocioId(socioId)).thenReturn(3L);

        var actions = new ArrayList<String>();
        service.deleteEmergencyContacts(socioId, actions);

        verify(contactRepository).deleteBySocioId(socioId);
        assertThat(actions).anyMatch(a -> a.contains("3 contacto"));
    }

    @Test
    @DisplayName("deleteMedicalInfo — existente → elimina y reporta")
    void deleteMedicalInfo_removes() {
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(true);

        var actions = new ArrayList<String>();
        service.deleteMedicalInfo(socioId, actions);

        verify(medicalInfoRepository).deleteBySocioId(socioId);
        assertThat(actions).hasSize(1);
    }

    @Test
    @DisplayName("deleteMedicalInfo — no existente → no hace nada")
    void deleteMedicalInfo_notExists_noOp() {
        when(medicalInfoRepository.existsBySocioId(socioId)).thenReturn(false);

        var actions = new ArrayList<String>();
        service.deleteMedicalInfo(socioId, actions);

        verify(medicalInfoRepository, never()).deleteBySocioId(any());
        assertThat(actions).isEmpty();
    }

    @Test
    @DisplayName("checkPendingFinancialObligations — delegado al repositorio")
    void checkPendingFinancialObligations_delegatesToRepo() {
        when(cuotaRepository.existsBySocioIdAndEstado(socioId, "PENDIENTE")).thenReturn(true);

        assertThat(service.checkPendingFinancialObligations(socioId)).isTrue();
    }
}
