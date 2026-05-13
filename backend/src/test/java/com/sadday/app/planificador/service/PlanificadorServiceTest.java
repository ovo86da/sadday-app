package com.sadday.app.planificador.service;

import com.sadday.app.informes.entity.InformeSalida;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.service.RutaService;
import com.sadday.app.planificador.dto.RecomendacionResponse;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanificadorService — Unit Tests")
class PlanificadorServiceTest {

    @Mock RutaService             rutaService;
    @Mock InformeSalidaRepository informeRepository;

    @InjectMocks PlanificadorService planificadorService;

    @Nested
    @DisplayName("recomendar")
    class Recomendar {

        @Test
        void rutaNoAprobada_lanzaBusinessException() {
            Ruta ruta = new Ruta();
            ruta.setId(1);
            ruta.setAprobada(false);
            when(rutaService.findRutaById(1)).thenReturn(ruta);

            var ex = assertThrows(BusinessException.class,
                    () -> planificadorService.recomendar(1));
            assertEquals(ErrorCode.RUTA_NOT_APPROVED, ex.getErrorCode());
        }

        @Test
        void sinInformes_retornaInsuficientesTrue() {
            Ruta ruta = rutaAprobada(1, "Cotopaxi");
            when(rutaService.findRutaById(1)).thenReturn(ruta);
            when(informeRepository.findBySalidaRutaId(1)).thenReturn(List.of());

            RecomendacionResponse response = planificadorService.recomendar(1);

            assertTrue(response.datosInsuficientes());
            assertEquals(0, response.totalSalidasPrevias());
            assertNull(response.tasaExitoPct());
        }

        @Test
        void menosDeTresSalidas_insuficientesTrue() {
            Ruta ruta = rutaAprobada(2, "Chimborazo");
            when(rutaService.findRutaById(2)).thenReturn(ruta);
            when(informeRepository.findBySalidaRutaId(2))
                    .thenReturn(List.of(informeRealizado(true)));

            RecomendacionResponse response = planificadorService.recomendar(2);

            assertTrue(response.datosInsuficientes());
            assertEquals(1, response.totalSalidasPrevias());
        }

        @Test
        void tresSalidasOmas_insuficientesFalse() {
            Ruta ruta = rutaAprobada(3, "Cayambe");
            when(rutaService.findRutaById(3)).thenReturn(ruta);
            when(informeRepository.findBySalidaRutaId(3))
                    .thenReturn(List.of(
                            informeRealizado(true),
                            informeRealizado(true),
                            informeRealizado(false)
                    ));

            RecomendacionResponse response = planificadorService.recomendar(3);

            assertFalse(response.datosInsuficientes());
            assertEquals(3, response.totalSalidasPrevias());
        }

        @Test
        void calculaTasaExito() {
            Ruta ruta = rutaAprobada(4, "Antisana");
            when(rutaService.findRutaById(4)).thenReturn(ruta);
            when(informeRepository.findBySalidaRutaId(4))
                    .thenReturn(List.of(
                            informeRealizado(true),
                            informeRealizado(true),
                            informeRealizado(false),
                            informeRealizado(true)
                    ));

            RecomendacionResponse response = planificadorService.recomendar(4);

            assertEquals(75.0, response.tasaExitoPct());
        }

        @Test
        void calculaHoraPromedio() {
            Ruta ruta = rutaAprobada(5, "Ilinizas");
            when(rutaService.findRutaById(5)).thenReturn(ruta);

            InformeSalida i1 = informeRealizado(true);
            i1.setHoraSalidaClub(LocalTime.of(6, 0));
            InformeSalida i2 = informeRealizado(true);
            i2.setHoraSalidaClub(LocalTime.of(8, 0));
            InformeSalida i3 = informeRealizado(true);
            i3.setHoraSalidaClub(LocalTime.of(7, 0));

            when(informeRepository.findBySalidaRutaId(5)).thenReturn(List.of(i1, i2, i3));

            RecomendacionResponse response = planificadorService.recomendar(5);

            assertEquals("07:00", response.horaSalidaPromedioClub());
        }

        @Test
        void sinHoraSalida_horaPromedioNull() {
            Ruta ruta = rutaAprobada(6, "Pichincha");
            when(rutaService.findRutaById(6)).thenReturn(ruta);
            when(informeRepository.findBySalidaRutaId(6))
                    .thenReturn(List.of(informeRealizado(true)));

            RecomendacionResponse response = planificadorService.recomendar(6);

            assertNull(response.horaSalidaPromedioClub());
        }

        @Test
        void calculaCostoGuia() {
            Ruta ruta = rutaAprobada(7, "Tungurahua");
            when(rutaService.findRutaById(7)).thenReturn(ruta);

            InformeSalida i1 = informeRealizado(true);
            i1.setAlquiloGuia(true);
            i1.setCostoGuia(new BigDecimal("50.00"));

            InformeSalida i2 = informeRealizado(true);
            i2.setAlquiloGuia(true);
            i2.setCostoGuia(new BigDecimal("100.00"));

            InformeSalida i3 = informeRealizado(true);
            i3.setAlquiloGuia(false);

            when(informeRepository.findBySalidaRutaId(7)).thenReturn(List.of(i1, i2, i3));

            RecomendacionResponse response = planificadorService.recomendar(7);

            assertEquals(75.0, response.costoPromedioGuia());
        }

        @Test
        void sinGuia_pctGuiaEs0() {
            Ruta ruta = rutaAprobada(8, "Imbabura");
            when(rutaService.findRutaById(8)).thenReturn(ruta);
            when(informeRepository.findBySalidaRutaId(8))
                    .thenReturn(List.of(informeRealizado(true)));

            RecomendacionResponse response = planificadorService.recomendar(8);

            assertEquals(0.0, response.pctContratoGuia());
        }

        @Test
        void retornaInfoBasicaDeLaRuta() {
            Ruta ruta = rutaAprobada(9, "Corazón");
            ruta.setLugarReferencia("Machachi");
            when(rutaService.findRutaById(9)).thenReturn(ruta);
            when(informeRepository.findBySalidaRutaId(9)).thenReturn(List.of());

            RecomendacionResponse response = planificadorService.recomendar(9);

            assertEquals(9, response.rutaId());
            assertEquals("Corazón", response.rutaNombre());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ruta rutaAprobada(Integer id, String nombre) {
        Ruta ruta = new Ruta();
        ruta.setId(id);
        ruta.setNombre(nombre);
        ruta.setAprobada(true);
        return ruta;
    }

    private InformeSalida informeRealizado(boolean seRealizo) {
        InformeSalida informe = new InformeSalida();
        informe.setSeRealizo(seRealizo);
        informe.setSegmentos(new ArrayList<>());
        return informe;
    }
}
