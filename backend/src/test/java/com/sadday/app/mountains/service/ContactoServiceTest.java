package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.entity.Contacto;
import com.sadday.app.mountains.entity.ContactoRuta;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.repository.ContactoRepository;
import com.sadday.app.mountains.repository.ContactoRutaRepository;
import com.sadday.app.mountains.repository.RutaRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContactoService — Unit Tests")
class ContactoServiceTest {

    @Mock ContactoRepository      contactoRepository;
    @Mock ContactoRutaRepository  contactoRutaRepository;
    @Mock RutaRepository          rutaRepository;

    @InjectMocks ContactoService contactoService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private Contacto mockContacto(Integer id) {
        Contacto c = new Contacto();
        c.setId(id);
        c.setNombre("Guía Test");
        c.setTelefono("0991234567");
        c.setCorreo("guia@test.com");
        return c;
    }

    private Ruta mockRuta(Integer id) {
        return Ruta.builder().id(id).nombre("Ruta Test").build();
    }

    /** Stub mínimo para que toResponse() no falle. */
    private void stubToResponse(Contacto c) {
        when(contactoRutaRepository.findTiposActivosByContactoId(c.getId()))
                .thenReturn(List.of());
    }

    // =========================================================================
    // obtener
    // =========================================================================

    @Nested
    @DisplayName("obtener")
    class Obtener {

        @Test
        @DisplayName("obtener — ID no encontrado → lanza RESOURCE_NOT_FOUND")
        void obtener_noEncontrado_lanzaResourceNotFound() {
            when(contactoRepository.findById(99)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contactoService.obtener(99));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }
    }

    // =========================================================================
    // crear
    // =========================================================================

    @Nested
    @DisplayName("crear")
    class Crear {

        @Test
        @DisplayName("crear — teléfono duplicado → lanza RESOURCE_CONFLICT")
        void crear_telefonoDuplicado_lanzaResourceConflict() {
            Contacto existente = mockContacto(1);
            when(contactoRepository.findByTelefono("0991234567"))
                    .thenReturn(Optional.of(existente));

            CreateGlobalContactoRequest request =
                    new CreateGlobalContactoRequest("Otro", "0991234567", "otro@test.com", null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contactoService.crear(request));

            assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
        }

        @Test
        @DisplayName("crear — teléfono null → guarda sin verificar duplicado")
        void crear_telefonoNull_guarda() {
            Contacto guardado = mockContacto(2);
            guardado.setTelefono(null);
            when(contactoRepository.save(any(Contacto.class))).thenReturn(guardado);
            stubToResponse(guardado);

            CreateGlobalContactoRequest request =
                    new CreateGlobalContactoRequest("Sin Teléfono", null, "st@test.com", null);

            GlobalContactoResponse resp = contactoService.crear(request);

            assertNotNull(resp);
            verify(contactoRepository, never()).findByTelefono(any());
            verify(contactoRepository).save(any(Contacto.class));
        }

        @Test
        @DisplayName("crear — teléfono válido único → guarda y retorna response")
        void crear_telefonoValido_guarda() {
            when(contactoRepository.findByTelefono("0999999999")).thenReturn(Optional.empty());

            Contacto guardado = mockContacto(3);
            guardado.setTelefono("0999999999");
            when(contactoRepository.save(any(Contacto.class))).thenReturn(guardado);
            stubToResponse(guardado);

            CreateGlobalContactoRequest request =
                    new CreateGlobalContactoRequest("Nuevo", "0999999999", "nuevo@test.com", null);

            GlobalContactoResponse resp = contactoService.crear(request);

            assertNotNull(resp);
            assertEquals(3, resp.id());
            verify(contactoRepository).save(any(Contacto.class));
        }
    }

    // =========================================================================
    // actualizar
    // =========================================================================

    @Nested
    @DisplayName("actualizar")
    class Actualizar {

        @Test
        @DisplayName("actualizar — teléfono duplicado de otro contacto → lanza RESOURCE_CONFLICT")
        void actualizar_telefonoDuplicadoOtroContacto_lanzaResourceConflict() {
            Contacto existente = mockContacto(1);
            existente.setTelefono("0991111111");
            when(contactoRepository.findById(1)).thenReturn(Optional.of(existente));

            Contacto otro = mockContacto(2);
            otro.setTelefono("0992222222");
            when(contactoRepository.findByTelefono("0992222222")).thenReturn(Optional.of(otro));

            UpdateGlobalContactoRequest request =
                    new UpdateGlobalContactoRequest("Nombre", "0992222222", "c@test.com", null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contactoService.actualizar(1, request));

            assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
        }

        @Test
        @DisplayName("actualizar — mismo teléfono que el contacto actual → no lanza conflicto")
        void actualizar_mismaTelefono_noLanzaConflict() {
            Contacto existente = mockContacto(1);
            existente.setTelefono("0991234567");
            when(contactoRepository.findById(1)).thenReturn(Optional.of(existente));
            when(contactoRepository.save(any(Contacto.class))).thenReturn(existente);
            stubToResponse(existente);

            // El mismo teléfono que ya tiene: la condición !equals falla → no hay búsqueda de duplicado
            UpdateGlobalContactoRequest request =
                    new UpdateGlobalContactoRequest("Guía Test", "0991234567", "guia@test.com", null);

            assertDoesNotThrow(() -> contactoService.actualizar(1, request));
            verify(contactoRepository, never()).findByTelefono(any());
        }
    }

    // =========================================================================
    // eliminar
    // =========================================================================

    @Nested
    @DisplayName("eliminar")
    class Eliminar {

        @Test
        @DisplayName("eliminar — contacto válido → llama deleteById")
        void eliminar_contactoValido_eliminaPorId() {
            Contacto c = mockContacto(1);
            when(contactoRepository.findById(1)).thenReturn(Optional.of(c));

            contactoService.eliminar(1);

            verify(contactoRepository).deleteById(1);
        }

        @Test
        @DisplayName("eliminar — contacto no encontrado → lanza RESOURCE_NOT_FOUND")
        void eliminar_noEncontrado_lanzaResourceNotFound() {
            when(contactoRepository.findById(99)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contactoService.eliminar(99));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }
    }

    // =========================================================================
    // upsertByPhone
    // =========================================================================

    @Nested
    @DisplayName("upsertByPhone")
    class UpsertByPhone {

        @Test
        @DisplayName("upsertByPhone — teléfono existente → retorna contacto existente sin guardar")
        void upsertByPhone_telefonoExistente_retornaContactoExistente() {
            Contacto existente = mockContacto(1);
            when(contactoRepository.findByTelefono("0991234567"))
                    .thenReturn(Optional.of(existente));

            Contacto result = contactoService.upsertByPhone("Guía", "0991234567", "g@test.com");

            assertSame(existente, result);
            verify(contactoRepository, never()).save(any());
        }

        @Test
        @DisplayName("upsertByPhone — teléfono nuevo → crea y guarda")
        void upsertByPhone_telefonoNuevo_creaYGuarda() {
            when(contactoRepository.findByTelefono("0990000000")).thenReturn(Optional.empty());
            Contacto nuevo = mockContacto(5);
            when(contactoRepository.save(any(Contacto.class))).thenReturn(nuevo);

            Contacto result = contactoService.upsertByPhone("Nuevo", "0990000000", "n@test.com");

            assertNotNull(result);
            verify(contactoRepository).save(any(Contacto.class));
        }

        @Test
        @DisplayName("upsertByPhone — teléfono null → crea contacto sin teléfono")
        void upsertByPhone_telefonoNull_creaSinTelefono() {
            Contacto sinTelefono = mockContacto(6);
            sinTelefono.setTelefono(null);
            when(contactoRepository.save(any(Contacto.class))).thenReturn(sinTelefono);

            Contacto result = contactoService.upsertByPhone("Sin Tel", null, "st@test.com");

            assertNotNull(result);
            verify(contactoRepository, never()).findByTelefono(any());
            verify(contactoRepository).save(any(Contacto.class));
        }
    }

    // =========================================================================
    // vincularARuta
    // =========================================================================

    @Nested
    @DisplayName("vincularARuta")
    class VincularARuta {

        @Test
        @DisplayName("vincularARuta — ruta no encontrada → lanza RUTA_NOT_FOUND")
        void vincularARuta_rutaNoEncontrada_lanzaRutaNotFound() {
            Contacto c = mockContacto(1);
            when(contactoRepository.findById(1)).thenReturn(Optional.of(c));
            when(rutaRepository.findById(99)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contactoService.vincularARuta(1, 99, "GUIA"));

            assertEquals(ErrorCode.RUTA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("vincularARuta — vínculo ya existe → no guarda de nuevo")
        void vincularARuta_yaVinculado_noGuardaDenuevo() {
            Contacto c = mockContacto(1);
            Ruta ruta = mockRuta(10);
            when(contactoRepository.findById(1)).thenReturn(Optional.of(c));
            when(rutaRepository.findById(10)).thenReturn(Optional.of(ruta));

            ContactoRuta crExistente = mock(ContactoRuta.class);
            when(crExistente.getContacto()).thenReturn(c);
            when(crExistente.getTipoContacto()).thenReturn("GUIA");
            when(contactoRutaRepository.findActivosByRutaId(10))
                    .thenReturn(List.of(crExistente));

            contactoService.vincularARuta(1, 10, "GUIA");

            verify(contactoRutaRepository, never()).save(any());
        }

        @Test
        @DisplayName("vincularARuta — no vinculado → guarda nuevo vínculo")
        void vincularARuta_noVinculado_guarda() {
            Contacto c = mockContacto(1);
            Ruta ruta = mockRuta(10);
            when(contactoRepository.findById(1)).thenReturn(Optional.of(c));
            when(rutaRepository.findById(10)).thenReturn(Optional.of(ruta));
            when(contactoRutaRepository.findActivosByRutaId(10)).thenReturn(List.of());

            contactoService.vincularARuta(1, 10, "GUIA");

            verify(contactoRutaRepository).save(any(ContactoRuta.class));
        }
    }

    // =========================================================================
    // vincular
    // =========================================================================

    @Nested
    @DisplayName("vincular")
    class Vincular {

        @Test
        @DisplayName("vincular — datos válidos → guarda y retorna ContactoRutaResponse")
        void vincular_guardaVinculo() {
            Contacto c = mockContacto(1);
            Ruta ruta = mockRuta(10);
            when(contactoRepository.findById(1)).thenReturn(Optional.of(c));
            when(rutaRepository.findById(10)).thenReturn(Optional.of(ruta));

            ContactoRuta crGuardado = ContactoRuta.builder()
                    .id(99)
                    .contacto(c)
                    .ruta(ruta)
                    .tipoContacto("TRANSPORTE")
                    .activo(true)
                    .build();
            when(contactoRutaRepository.save(any(ContactoRuta.class))).thenReturn(crGuardado);

            VincularContactoRutaRequest request = new VincularContactoRutaRequest(1, "TRANSPORTE");
            ContactoRutaResponse resp = contactoService.vincular(10, request);

            assertNotNull(resp);
            assertEquals(99, resp.id());
            assertEquals("TRANSPORTE", resp.tipoContacto());
            verify(contactoRutaRepository).save(any(ContactoRuta.class));
        }
    }

    // =========================================================================
    // desvincular
    // =========================================================================

    @Nested
    @DisplayName("desvincular")
    class Desvincular {

        @Test
        @DisplayName("desvincular — vínculo pertenece a otra ruta → lanza RESOURCE_NOT_FOUND")
        void desvincular_vinculoDeOtraRuta_lanzaResourceNotFound() {
            Ruta otraRuta = mockRuta(999);
            ContactoRuta cr = mock(ContactoRuta.class);
            when(cr.getRuta()).thenReturn(otraRuta);
            when(contactoRutaRepository.findById(5)).thenReturn(Optional.of(cr));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contactoService.desvincular(10, 5));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("desvincular — vínculo válido → setActivo(false)")
        void desvincular_vinculoValido_setActivoFalse() {
            Ruta ruta = mockRuta(10);
            ContactoRuta cr = mock(ContactoRuta.class);
            when(cr.getRuta()).thenReturn(ruta);
            when(contactoRutaRepository.findById(5)).thenReturn(Optional.of(cr));

            contactoService.desvincular(10, 5);

            verify(cr).setActivo(false);
        }

        @Test
        @DisplayName("desvincular — vínculo no encontrado → lanza RESOURCE_NOT_FOUND")
        void desvincular_vinculoNoEncontrado_lanzaResourceNotFound() {
            when(contactoRutaRepository.findById(99)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contactoService.desvincular(10, 99));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }
    }
}
