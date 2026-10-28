package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.EvaluacionNivelResponse;
import com.sadday.app.mountains.dto.EvaluacionNivelResponse.Hallazgo;
import com.sadday.app.mountains.entity.*;
import com.sadday.app.mountains.repository.AccesoRutaPorNivelRepository;
import com.sadday.app.mountains.repository.DificultadCiclismoStcRepository;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.repository.ClasificacionSocioRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.when;

/**
 * Lo que la matriz de acceso dice sobre un socio y una ruta.
 *
 * <p>Este servicio es el que FR-028 añadió para cerrar el agujero: la matriz
 * describía con nueve escalas qué puede intentar cada nivel y no la leía nadie.
 * Si se equivoca, se equivoca en los dos sentidos peligrosos —deja entrar a quien
 * no debe, o retiene a quien sí podía— y en el primero nadie se entera.
 *
 * <p>Lo que más se prueba aquí es el segundo caso de la pareja: no basta con
 * detectar lo que excede, hay que <b>no</b> dar por bueno lo que no se ha podido
 * comprobar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NivelRutaService — cruzar la ruta con lo que permite el nivel")
class NivelRutaServiceTest {

    @Mock AccesoRutaPorNivelRepository    accesoRepository;
    @Mock ClasificacionSocioRepository    clasificacionRepository;
    @Mock DificultadCiclismoStcRepository dificultadCiclismoRepo;

    @InjectMocks NivelRutaService service;

    private ClasificacionSocio principiante;
    private AccesoRutaPorNivel techoPrincipiante;

    // catálogos
    private static EscalaAlpinaIfas      ifas(String g, int r)  { return EscalaAlpinaIfas.builder().id(g).grado(g).rank((short) r).build(); }
    private static DificultadRoca        roca(String g, int r)  { return DificultadRoca.builder().id(g).uiaa(g).rank((short) r).build(); }
    private static DificultadHielo       hielo(String g, int r) { return DificultadHielo.builder().id(g).grado(g).rank((short) r).build(); }
    private static Compromiso            comp(String t, int r)  { return Compromiso.builder().id(t).tipo(t).rank((short) r).build(); }
    private static SistemaClasesYosemite yds(String t, int r)   { return SistemaClasesYosemite.builder().id(t).tipo(t).rank((short) r).build(); }
    private static SaddayRiesgoExigencia sadday(String e, int r){ return SaddayRiesgoExigencia.builder().id(e).escala(e).rank((short) r).build(); }
    private static DificultadCiclismoStc ciclo(String n, int r) { return DificultadCiclismoStc.builder().id(n).nombre(n).rank((short) r).build(); }

    private static DificultadSenderismo sender(String n, int r) {
        DificultadSenderismo d = new DificultadSenderismo();
        d.setId(n); d.setNombre(n); d.setRank((short) r);
        return d;
    }

    @BeforeEach
    void setUp() {
        principiante = ClasificacionSocio.builder().id("SO002").nivel((short) 1).nombre("Principiante").build();

        // El techo de un principiante: alpinismo puesto, senderismo y ciclismo sin fijar.
        techoPrincipiante = AccesoRutaPorNivel.builder()
                .id((short) 1).nivelSocio(principiante)
                .maxIfas(ifas("PD", 2)).maxRoca(roca("III", 3)).maxHielo(hielo("WI2", 2))
                .maxCompromiso(comp("I", 1)).maxYosemite(yds("Clase 3", 3))
                .maxSaddayTecnico(sadday("Medio", 3)).maxSaddayFisico(sadday("Medio", 3))
                .build();

        when(accesoRepository.findByNivelSocioId("SO002")).thenReturn(Optional.of(techoPrincipiante));
    }

    // ── rutas de prueba ──────────────────────────────────────────────────────

    private Ruta rutaAlpina(EscalaAlpinaIfas i, DificultadRoca r, DificultadHielo h,
                            Compromiso c, SistemaClasesYosemite y,
                            SaddayRiesgoExigencia tec, SaddayRiesgoExigencia fis) {
        Ruta ruta = Ruta.builder().id(1).nombre("Cara norte").tipoActividad(TipoActividad.ALPINISMO).build();
        ruta.setAlpinismo(RutaAlpinismo.builder()
                .ruta(ruta).escalaAlpinaIfas(i).dificultadRoca(r).dificultadHielo(h)
                .compromiso(c).yosemite(y).saddayNivelTecnico(tec).saddayNivelFisico(fis)
                .build());
        return ruta;
    }

    /** Una ruta alpina que un principiante sí puede hacer. */
    private Ruta rutaAlpinaFacil() {
        return rutaAlpina(ifas("F", 1), roca("II", 2), hielo("WI1", 1),
                          comp("I", 1), yds("Clase 2", 2), sadday("Bajo", 2), sadday("Bajo", 2));
    }

    private Ruta rutaDeTipo(TipoActividad tipo) {
        return Ruta.builder().id(2).nombre("X").tipoActividad(tipo).build();
    }

    private List<String> escalas(EvaluacionNivelResponse e) {
        return e.hallazgos().stream().map(Hallazgo::escala).toList();
    }

    // ── el caso que cierra el agujero ────────────────────────────────────────

    @Test
    @DisplayName("una ruta por encima del nivel: dice qué escalas y con qué valores")
    void diceQueEscalaNoDa() {
        Ruta dura = rutaAlpina(ifas("F", 1), roca("VI", 6), hielo("WI1", 1),
                               comp("III", 3), yds("Clase 2", 2),
                               sadday("Bajo", 2), sadday("Bajo", 2));

        EvaluacionNivelResponse e = service.evaluar(dura, principiante);

        assertFalse(e.apto());
        assertTrue(e.algoExcede());
        assertFalse(e.algoSinComprobar(), "aquí no falta ningún dato, solo no alcanza");
        assertEquals(List.of("Roca", "Compromiso"), escalas(e),
                "solo las que no dan: los hallazgos son lo que impide, no un informe");

        Hallazgo roca = e.hallazgos().get(0);
        assertEquals("VI", roca.exigeRuta());
        assertEquals("III", roca.permiteNivel());
        assertEquals(EvaluacionNivelResponse.EXCEDE, roca.tipo());
        assertTrue(roca.detalle().contains("VI") && roca.detalle().contains("III"),
                "el detalle lleva los dos valores: " + roca.detalle());
    }

    @Test
    @DisplayName("una ruta dentro del nivel pasa sin hallazgos")
    void rutaDentroDelNivel() {
        EvaluacionNivelResponse e = service.evaluar(rutaAlpinaFacil(), principiante);

        assertTrue(e.apto(), "esta ruta le da: " + escalas(e));
        assertTrue(e.hallazgos().isEmpty());
    }

    @Test
    @DisplayName("igualar el techo exacto da, no excede")
    void igualarElTechoDa() {
        Ruta justa = rutaAlpina(ifas("PD", 2), roca("III", 3), hielo("WI2", 2),
                                comp("I", 1), yds("Clase 3", 3),
                                sadday("Medio", 3), sadday("Medio", 3));

        assertTrue(service.evaluar(justa, principiante).apto(),
                "el máximo permitido es alcanzable; si no, el techo sería inútil");
    }

    // ── lo que no se puede comprobar frena ───────────────────────────────────

    @Nested
    @DisplayName("un hueco de datos no es un visto bueno")
    class Huecos {

        @Test
        @DisplayName("un socio sin nivel técnico no pasa por defecto")
        void socioSinNivel() {
            EvaluacionNivelResponse e = service.evaluar(rutaAlpinaFacil(), null);

            assertFalse(e.apto(), "sin nivel no se puede comparar nada");
            assertTrue(e.algoSinComprobar());
            assertTrue(e.hallazgos().get(0).detalle().contains("nivel técnico"),
                    e.hallazgos().get(0).detalle());
        }

        @Test
        @DisplayName("un nivel sin umbrales definidos tampoco")
        void nivelSinUmbrales() {
            ClasificacionSocio otro = ClasificacionSocio.builder()
                    .id("SO009").nivel((short) 2).nombre("Nuevo").build();
            when(accesoRepository.findByNivelSocioId("SO009")).thenReturn(Optional.empty());

            EvaluacionNivelResponse e = service.evaluar(rutaAlpinaFacil(), otro);

            assertFalse(e.apto());
            assertTrue(e.hallazgos().get(0).detalle().contains("Nuevo"),
                    "dice de qué nivel se trata: " + e.hallazgos().get(0).detalle());
        }

        @Test
        @DisplayName("una salida sin ruta no se da por buena")
        void salidaSinRuta() {
            EvaluacionNivelResponse e = service.evaluar(null, principiante);

            assertFalse(e.apto());
            assertTrue(e.algoSinComprobar());
        }

        @Test
        @DisplayName("un trekking con el máximo de senderismo sin fijar frena")
        void trekkingConUmbralSinFijar() {
            Ruta t = rutaDeTipo(TipoActividad.TREKKING);
            t.setTrekking(RutaTrekking.builder().ruta(t).dificultad(sender("Moderado", 3)).build());

            EvaluacionNivelResponse e = service.evaluar(t, principiante);

            assertFalse(e.apto(), "«sin fijar» no es «sin límite»");
            assertTrue(e.algoSinComprobar());
            assertFalse(e.algoExcede(), "no se sabe si excede; eso es distinto de que exceda");
            assertEquals(List.of("Senderismo"), escalas(e));
            assertEquals("Moderado", e.hallazgos().get(0).exigeRuta());
            assertNull(e.hallazgos().get(0).permiteNivel(), "no hay techo que mostrar");
        }

        @Test
        @DisplayName("un ciclismo sin dificultad técnica frena")
        void ciclismoSinDificultad() {
            Ruta c = rutaDeTipo(TipoActividad.CICLISMO);
            c.setCiclismo(RutaCiclismo.builder().ruta(c).dificultadTecnica(null).build());

            assertFalse(service.evaluar(c, principiante).apto());
        }

        @Test
        @DisplayName("un grado de ciclismo que no está en el catálogo frena, y lo dice")
        void ciclismoFueraDeCatalogo() {
            Ruta c = rutaDeTipo(TipoActividad.CICLISMO);
            c.setCiclismo(RutaCiclismo.builder().ruta(c).dificultadTecnica("S9").build());
            when(dificultadCiclismoRepo.findById("S9")).thenReturn(Optional.empty());

            EvaluacionNivelResponse e = service.evaluar(c, principiante);

            assertFalse(e.apto());
            assertTrue(e.hallazgos().get(0).detalle().contains("S9"),
                    e.hallazgos().get(0).detalle());
        }

        @Test
        @DisplayName("una ruta sin su fila de dificultades frena")
        void rutaSinSusDificultades() {
            assertFalse(service.evaluar(rutaDeTipo(TipoActividad.ALPINISMO), principiante).apto());
            assertFalse(service.evaluar(rutaDeTipo(TipoActividad.ESCALADA), principiante).apto());
            assertFalse(service.evaluar(rutaDeTipo(TipoActividad.TREKKING), principiante).apto());
        }
    }

    // ── cada tipo trae lo suyo ───────────────────────────────────────────────

    @Nested
    @DisplayName("cada tipo de ruta se mide con las escalas que usa")
    class PorTipo {

        @Test
        @DisplayName("una escalada se mide solo por roca; las otras seis no aplican")
        void escaladaSoloRoca() {
            Ruta esc = rutaDeTipo(TipoActividad.ESCALADA);
            esc.setEscalada(RutaEscalada.builder().ruta(esc).dificultadRoca(roca("VI", 6)).build());

            EvaluacionNivelResponse e = service.evaluar(esc, principiante);

            assertEquals(List.of("Roca"), escalas(e),
                    "no se inventan huecos por las escalas que una vía de escalada no tiene");
        }

        @Test
        @DisplayName("un ciclismo dentro del techo pasa, cuando el techo está fijado")
        void ciclismoConTecho() {
            techoPrincipiante.setMaxCiclismo(ciclo("S2", 3));
            Ruta c = rutaDeTipo(TipoActividad.CICLISMO);
            c.setCiclismo(RutaCiclismo.builder().ruta(c).dificultadTecnica("S1").build());
            when(dificultadCiclismoRepo.findById("S1")).thenReturn(Optional.of(ciclo("S1", 2)));

            assertTrue(service.evaluar(c, principiante).apto());
        }

        @Test
        @DisplayName("una integral se mide con las siete escalas de alpinismo")
        void integralEsAlpinismo() {
            Ruta integral = rutaAlpina(ifas("F", 1), roca("VI", 6), hielo("WI1", 1),
                                       comp("I", 1), yds("Clase 2", 2),
                                       sadday("Bajo", 2), sadday("Bajo", 2));
            integral.setTipoActividad(TipoActividad.INTEGRAL);

            EvaluacionNivelResponse e = service.evaluar(integral, principiante);

            assertEquals(List.of("Roca"), escalas(e),
                    "una integral se clasifica como alpinismo y se compara igual");
        }

        @Test
        @DisplayName("una integral sin su clasificación de alpinismo frena, y lo dice")
        void integralSinClasificacion() {
            EvaluacionNivelResponse e = service.evaluar(rutaDeTipo(TipoActividad.INTEGRAL), principiante);

            assertFalse(e.apto());
            assertTrue(e.hallazgos().get(0).detalle().contains("integral"),
                    e.hallazgos().get(0).detalle());
        }
    }

    // ── el nivel mínimo derivado ─────────────────────────────────────────────

    @Nested
    @DisplayName("el nivel mínimo que la ruta exige, deducido de la matriz")
    class NivelMinimo {

        private ClasificacionSocio medio;

        @BeforeEach
        void niveles() {
            medio = ClasificacionSocio.builder().id("SO004").nivel((short) 3).nombre("Avanzado").build();
            AccesoRutaPorNivel techoMedio = AccesoRutaPorNivel.builder()
                    .id((short) 2).nivelSocio(medio)
                    .maxIfas(ifas("D", 4)).maxRoca(roca("VI", 6)).maxHielo(hielo("WI4", 4))
                    .maxCompromiso(comp("III", 3)).maxYosemite(yds("Clase 5", 5))
                    .maxSaddayTecnico(sadday("Alto", 5)).maxSaddayFisico(sadday("Alto", 5))
                    .build();
            when(accesoRepository.findByNivelSocioId("SO004")).thenReturn(Optional.of(techoMedio));
            when(clasificacionRepository.findAll()).thenReturn(List.of(medio, principiante));
        }

        @Test
        @DisplayName("devuelve el nivel más bajo que la permite, no el primero que encuentra")
        void elMasBajoQueLaPermite() {
            // El repositorio los devuelve desordenados a propósito.
            assertEquals("SO002", service.nivelMinimoDerivado(rutaAlpinaFacil()).getId(),
                    "un principiante ya puede con esta ruta");
        }

        @Test
        @DisplayName("una ruta dura sube el mínimo al nivel que sí la cubre")
        void rutaDuraSubeElMinimo() {
            Ruta dura = rutaAlpina(ifas("F", 1), roca("VI", 6), hielo("WI1", 1),
                                   comp("III", 3), yds("Clase 2", 2),
                                   sadday("Bajo", 2), sadday("Bajo", 2));

            assertEquals("SO004", service.nivelMinimoDerivado(dura).getId());
        }

        @Test
        @DisplayName("si ningún nivel la permite, devuelve null en vez de inventar uno")
        void ningunNivelLaPermite() {
            Ruta imposible = rutaAlpina(ifas("ED", 9), roca("IX", 9), hielo("WI7", 7),
                                        comp("V", 5), yds("Clase 6", 6),
                                        sadday("Extremo", 9), sadday("Extremo", 9));

            assertNull(service.nivelMinimoDerivado(imposible));
        }

        @Test
        @DisplayName("si falta un dato para saberlo, también null: no lo sé no es un número")
        void sinDatosEsNull() {
            Ruta t = rutaDeTipo(TipoActividad.TREKKING);
            t.setTrekking(RutaTrekking.builder().ruta(t).dificultad(sender("Moderado", 3)).build());

            assertNull(service.nivelMinimoDerivado(t),
                    "los máximos de senderismo están sin fijar en los dos niveles");
        }
    }
}
