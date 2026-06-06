package com.sadday.app.emergencycontacts.service;

import com.sadday.app.emergencycontacts.dto.EmergencyContactRequest;
import com.sadday.app.emergencycontacts.dto.EmergencyContactResponse;
import com.sadday.app.emergencycontacts.dto.UpsertEmergencyContactsRequest;
import com.sadday.app.emergencycontacts.entity.SocioEmergencyContact;
import com.sadday.app.emergencycontacts.repository.SocioEmergencyContactRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmergencyContactService — Unit Tests")
class EmergencyContactServiceTest {

    @Mock SocioEmergencyContactRepository contactRepository;
    @Mock SocioRepository                 socioRepository;

    @InjectMocks EmergencyContactService service;

    private UUID socioId;
    private Socio socio;

    @BeforeEach
    void setUp() {
        socioId = UUID.randomUUID();
        socio = Socio.builder().id(socioId).nombre("Test").apellido("Socio").build();
    }

    // =========================================================================
    // getMisPropios
    // =========================================================================

    @Test
    @DisplayName("getMisPropios — devuelve contactos ordenados")
    void getMisPropios_returnsContacts() {
        when(contactRepository.findBySocioIdOrderByOrden(socioId)).thenReturn(List.of(
                buildContact((short) 1, "Contacto Uno"),
                buildContact((short) 2, "Contacto Dos")));

        List<EmergencyContactResponse> result = service.getMisPropios(socioId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).orden()).isEqualTo((short) 1);
        assertThat(result.get(1).nombreCompleto()).isEqualTo("Contacto Dos");
    }

    // =========================================================================
    // upsertMisPropios
    // =========================================================================

    @Test
    @DisplayName("upsertMisPropios — reemplaza contactos y devuelve lista guardada")
    void upsertMisPropios_replacesAndReturns() {
        UpsertEmergencyContactsRequest req = new UpsertEmergencyContactsRequest(List.of(
                new EmergencyContactRequest((short) 1, "Nuevo Uno", "Familiar", "0991234567", "Quito"),
                new EmergencyContactRequest((short) 2, "Nuevo Dos", "Amigo", "0997654321", null)));

        when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
        when(contactRepository.save(any())).thenAnswer(inv -> {
            SocioEmergencyContact c = inv.getArgument(0);
            return SocioEmergencyContact.builder()
                    .id(UUID.randomUUID()).socio(c.getSocio()).orden(c.getOrden())
                    .nombreCompleto(c.getNombreCompleto()).relacion(c.getRelacion())
                    .celular(c.getCelular()).direccion(c.getDireccion()).build();
        });

        List<EmergencyContactResponse> result = service.upsertMisPropios(socioId, req);

        assertThat(result).hasSize(2);
        verify(contactRepository).deleteBySocioId(socioId);
        verify(contactRepository).flush();
        verify(contactRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("upsertMisPropios — órdenes duplicados → VALIDATION_ERROR")
    void upsertMisPropios_duplicateOrdenes_throws() {
        UpsertEmergencyContactsRequest req = new UpsertEmergencyContactsRequest(List.of(
                new EmergencyContactRequest((short) 1, "Uno", "Familiar", null, null),
                new EmergencyContactRequest((short) 1, "Otro Uno", "Amigo", null, null)));

        assertThatThrownBy(() -> service.upsertMisPropios(socioId, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("upsertMisPropios — socio no existe → SOCIO_NOT_FOUND")
    void upsertMisPropios_socioNotFound_throws() {
        UpsertEmergencyContactsRequest req = new UpsertEmergencyContactsRequest(List.of(
                new EmergencyContactRequest((short) 1, "Uno", "Familiar", null, null)));
        when(socioRepository.findById(socioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertMisPropios(socioId, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIO_NOT_FOUND);
    }

    // =========================================================================
    // getBySocioId (admin)
    // =========================================================================

    @Test
    @DisplayName("getBySocioId — socio no existe → SOCIO_NOT_FOUND")
    void getBySocioId_socioNotFound_throws() {
        when(socioRepository.existsById(socioId)).thenReturn(false);

        assertThatThrownBy(() -> service.getBySocioId(socioId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIO_NOT_FOUND);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private SocioEmergencyContact buildContact(short orden, String nombre) {
        return SocioEmergencyContact.builder()
                .id(UUID.randomUUID()).socio(socio).orden(orden)
                .nombreCompleto(nombre).relacion("Familiar")
                .celular("0991234567").build();
    }
}
