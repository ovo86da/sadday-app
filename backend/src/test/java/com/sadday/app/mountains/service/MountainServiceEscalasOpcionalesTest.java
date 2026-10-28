package com.sadday.app.mountains.service;

import com.sadday.app.mountains.entity.DificultadCiclismoStc;
import com.sadday.app.mountains.entity.DificultadSenderismo;
import com.sadday.app.mountains.repository.DificultadCiclismoStcRepository;
import com.sadday.app.mountains.repository.DificultadSenderismoRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Las dos escalas que pueden quedar «sin fijar».
 *
 * <p>Senderismo y ciclismo llegaron a la matriz con FR-028 y son nulables a
 * propósito: «sin fijar» no es «sin límite». Por eso se resuelven con un buscador
 * aparte que acepta el vacío, mientras las siete de alpinismo son obligatorias.
 *
 * <p>Lo que se fija aquí es la diferencia entre las dos formas de no haber nada:
 * <b>no mandar valor</b> es válido y da null, pero <b>mandar un id que no
 * existe</b> es un error y no puede confundirse con lo primero. Si el buscador
 * devolviera null ante un id desconocido, un typo en la pantalla borraría el
 * límite en silencio —y pasaría por la doble aprobación como «SIN_FIJAR»—.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MountainService — senderismo y ciclismo, que pueden ir sin fijar")
class MountainServiceEscalasOpcionalesTest {

    @Mock DificultadSenderismoRepository  dificultadSenderismoRepo;
    @Mock DificultadCiclismoStcRepository dificultadCiclismoRepo;

    @InjectMocks MountainService service;

    @ParameterizedTest(name = "senderismo con [{0}] → sin fijar")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("no mandar valor deja la escala sin fijar, sin ir a la base")
    void senderismoVacioEsNull(String id) {
        assertNull(service.findSenderismoOpcional(id));
        verify(dificultadSenderismoRepo, never()).findById(any());
    }

    @ParameterizedTest(name = "ciclismo con [{0}] → sin fijar")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("lo mismo para ciclismo")
    void ciclismoVacioEsNull(String id) {
        assertNull(service.findCiclismoOpcional(id));
        verify(dificultadCiclismoRepo, never()).findById(any());
    }

    @Test
    @DisplayName("un id que existe se resuelve")
    void idValidoSeResuelve() {
        DificultadSenderismo moderado = new DificultadSenderismo();
        moderado.setId("MOD"); moderado.setNombre("Moderado"); moderado.setRank((short) 3);
        when(dificultadSenderismoRepo.findById("MOD")).thenReturn(Optional.of(moderado));

        DificultadCiclismoStc s2 = DificultadCiclismoStc.builder()
                .id("S2").nombre("S2").rank((short) 3).build();
        when(dificultadCiclismoRepo.findById("S2")).thenReturn(Optional.of(s2));

        assertEquals("Moderado", service.findSenderismoOpcional("MOD").getNombre());
        assertEquals("S2", service.findCiclismoOpcional("S2").getId());
    }

    @Test
    @DisplayName("un id que no existe es un error, no un «sin fijar»")
    void idDesconocidoNoEsSinFijar() {
        when(dificultadSenderismoRepo.findById("XX")).thenReturn(Optional.empty());
        when(dificultadCiclismoRepo.findById("S9")).thenReturn(Optional.empty());

        BusinessException s = assertThrows(BusinessException.class,
                () -> service.findSenderismoOpcional("XX"));
        BusinessException c = assertThrows(BusinessException.class,
                () -> service.findCiclismoOpcional("S9"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, s.getErrorCode(),
                "un typo no puede borrar el límite en silencio");
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, c.getErrorCode());
        assertTrue(s.getMessage().contains("XX"), "el mensaje dice qué id falló: " + s.getMessage());
    }
}
