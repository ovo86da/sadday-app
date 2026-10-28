package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.CambioUmbralResponse;
import com.sadday.app.mountains.dto.CambioUmbralResponse.CambioEscala;
import com.sadday.app.mountains.entity.*;
import com.sadday.app.mountains.entity.AccesoNivelCambioPendiente.Estado;
import com.sadday.app.mountains.repository.AccesoNivelCambioPendienteRepository;
import com.sadday.app.mountains.repository.AccesoRutaPorNivelRepository;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Qué se le dice al segundo aprobador sobre cada escala.
 *
 * <p>FR-026 exige que una segunda persona apruebe un cambio de umbral, y para
 * decidir necesita saber en qué sentido va cada escala. Ese cálculo depende del
 * {@code rank}, que el cliente no conoce, así que si aquí sale mal nadie lo ve:
 * la pantalla mostrará con toda confianza «ENDURECE» sobre algo que relaja.
 *
 * <p>FR-028 añadió senderismo y ciclismo, que pueden estar <b>sin fijar</b>, y
 * con ellos cuatro combinaciones de nulos. La integración no las cubre —exige
 * una fila por escala en la base— y son justo donde es fácil equivocarse:
 * tratar un nulo como cero haría que «sin fijar → S1» pareciera endurecer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CambioUmbralService — qué se le dice al segundo aprobador")
class CambioUmbralComparacionTest {

    @Mock AccesoNivelCambioPendienteRepository cambioRepository;
    @Mock AccesoRutaPorNivelRepository         accesoRepository;
    @Mock MountainService                      mountainService;
    @Mock CambioUmbralMailSender               mailSender;

    @InjectMocks CambioUmbralService service;

    private static final UUID QUIEN = UUID.randomUUID();
    private static final UUID OTRO  = UUID.randomUUID();

    private ClasificacionSocio nivel;

    @BeforeEach
    void setUp() {
        nivel = ClasificacionSocio.builder().id("ESCALADOR").nivel((short) 3).nombre("Escalador").build();
        when(mountainService.findSocio(QUIEN)).thenReturn(socio(QUIEN));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Socio socio(UUID id) {
        return Socio.builder().id(id).nombre("Ana").apellido("Pérez").build();
    }

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

    /** La matriz vigente, con las siete de alpinismo puestas y las dos de FR-028 sin fijar. */
    private AccesoRutaPorNivel vigente() {
        return AccesoRutaPorNivel.builder()
                .id((short) 1).nivelSocio(nivel)
                .maxIfas(ifas("F", 2)).maxRoca(roca("III", 3)).maxHielo(hielo("WI2", 2))
                .maxCompromiso(comp("I", 1)).maxYosemite(yds("Clase 3", 3))
                .maxSaddayTecnico(sadday("Medio", 3)).maxSaddayFisico(sadday("Medio", 3))
                .build();
    }

    /** Un cambio pendiente que copia la matriz vigente: punto de partida para variar una escala. */
    private AccesoNivelCambioPendiente copiaDe(AccesoRutaPorNivel v) {
        return AccesoNivelCambioPendiente.builder()
                .id(UUID.randomUUID()).nivelSocio(nivel)
                .maxIfas(v.getMaxIfas()).maxRoca(v.getMaxRoca()).maxHielo(v.getMaxHielo())
                .maxCompromiso(v.getMaxCompromiso()).maxYosemite(v.getMaxYosemite())
                .maxSaddayTecnico(v.getMaxSaddayTecnico()).maxSaddayFisico(v.getMaxSaddayFisico())
                .maxSenderismo(v.getMaxSenderismo()).maxCiclismo(v.getMaxCiclismo())
                .solicitadoPor(socio(OTRO)).estado(Estado.PENDIENTE)
                .build();
    }

    /** Pide el detalle tal como lo vería un segundo aprobador, y devuelve escala → sentido. */
    private Map<String, CambioEscala> escalasDe(AccesoRutaPorNivel v, AccesoNivelCambioPendiente c) {
        when(cambioRepository.findPendientes()).thenReturn(List.of(c));
        when(accesoRepository.findByNivelSocioId(nivel.getId())).thenReturn(Optional.ofNullable(v));

        List<CambioUmbralResponse> lista = service.listarPendientes(QUIEN);
        assertEquals(1, lista.size(), "debería haber exactamente un cambio pendiente");
        return lista.get(0).escalas().stream()
                .collect(Collectors.toMap(CambioEscala::escala, Function.identity()));
    }

    private String sentido(AccesoRutaPorNivel v, AccesoNivelCambioPendiente c, String escala) {
        CambioEscala e = escalasDe(v, c).get(escala);
        assertNotNull(e, "no se comparó la escala «" + escala + "»");
        return e.sentido();
    }

    // ── las nueve escalas se comparan, siempre ───────────────────────────────

    @Test
    @DisplayName("se comparan las nueve escalas, no solo las siete de alpinismo")
    void nueveEscalas() {
        AccesoRutaPorNivel v = vigente();
        Map<String, CambioEscala> e = escalasDe(v, copiaDe(v));

        assertEquals(9, e.size(), "FR-028 extendió la matriz: deben compararse nueve escalas");
        assertTrue(e.keySet().containsAll(List.of(
                "IFAS", "Roca", "Hielo", "Compromiso", "Yosemite",
                "Técnico Sadday", "Físico Sadday", "Senderismo", "Ciclismo")),
                "faltan escalas: " + e.keySet());
    }

    @Test
    @DisplayName("sin cambios, las siete de alpinismo salen IGUAL")
    void sinCambiosTodoIgual() {
        AccesoRutaPorNivel v = vigente();
        Map<String, CambioEscala> e = escalasDe(v, copiaDe(v));

        for (String escala : List.of("IFAS", "Roca", "Hielo", "Compromiso", "Yosemite",
                                     "Técnico Sadday", "Físico Sadday")) {
            assertEquals("IGUAL", e.get(escala).sentido(), escala + " no cambió");
        }
    }

    // ── las cuatro combinaciones de nulos (FR-028) ───────────────────────────

    @Nested
    @DisplayName("«sin fijar» no es «sin límite», y se dice tal cual")
    class Nulos {

        @Test
        @DisplayName("nulo en los dos lados → SIN_FIJAR, y sin inventar valores")
        void nuloEnAmbos() {
            AccesoRutaPorNivel v = vigente();          // senderismo y ciclismo sin fijar
            Map<String, CambioEscala> e = escalasDe(v, copiaDe(v));

            for (String escala : List.of("Senderismo", "Ciclismo")) {
                CambioEscala c = e.get(escala);
                assertEquals("SIN_FIJAR", c.sentido(), escala);
                assertNull(c.valorActual(),    escala + ": no hay valor actual que mostrar");
                assertNull(c.valorPropuesto(), escala + ": no hay valor propuesto que mostrar");
            }
        }

        @Test
        @DisplayName("se fija por primera vez → SE_FIJA, nunca RELAJA ni ENDURECE")
        void seFijaPorPrimeraVez() {
            AccesoRutaPorNivel v = vigente();
            AccesoNivelCambioPendiente c = copiaDe(v);
            c.setMaxSenderismo(sender("Moderado", 3));
            c.setMaxCiclismo(ciclo("S1", 2));

            Map<String, CambioEscala> e = escalasDe(v, c);

            assertEquals("SE_FIJA", e.get("Senderismo").sentido(),
                    "fijar un límite que no existía no es relajar ni endurecer");
            assertEquals("SE_FIJA", e.get("Ciclismo").sentido());
            assertNull(e.get("Senderismo").valorActual());
            assertEquals("Moderado", e.get("Senderismo").valorPropuesto());
            assertEquals("S1", e.get("Ciclismo").valorPropuesto());
        }

        @Test
        @DisplayName("quitar un límite que existía → RELAJA: permite más")
        void quitarLimiteRelaja() {
            AccesoRutaPorNivel v = vigente();
            v.setMaxSenderismo(sender("Bajo", 2));
            v.setMaxCiclismo(ciclo("S1", 2));

            AccesoNivelCambioPendiente c = copiaDe(v);
            c.setMaxSenderismo(null);
            c.setMaxCiclismo(null);

            Map<String, CambioEscala> e = escalasDe(v, c);

            assertEquals("RELAJA", e.get("Senderismo").sentido(),
                    "quitar el techo permite más, aunque no sea un grado concreto");
            assertEquals("RELAJA", e.get("Ciclismo").sentido());
            assertEquals("Bajo", e.get("Senderismo").valorActual());
            assertNull(e.get("Senderismo").valorPropuesto());
        }

        @Test
        @DisplayName("un nulo no se trata como cero: «sin fijar → S1» no endurece")
        void nuloNoEsCero() {
            AccesoRutaPorNivel v = vigente();          // ciclismo sin fijar
            AccesoNivelCambioPendiente c = copiaDe(v);
            c.setMaxCiclismo(ciclo("S1", 2));          // rank 2 > un hipotético 0

            assertNotEquals("ENDURECE", sentido(v, c, "Ciclismo"),
                    "si el nulo se comparara como rank 0, esto saldría ENDURECE");
            assertEquals("SE_FIJA", sentido(v, c, "Ciclismo"));
        }
    }

    // ── el sentido, cuando hay valor en los dos lados ────────────────────────

    @Nested
    @DisplayName("el sentido sale del rank, no del texto")
    class Sentido {

        @Test
        @DisplayName("subir el techo RELAJA; bajarlo ENDURECE")
        void subirYBajar() {
            AccesoRutaPorNivel v = vigente();

            AccesoNivelCambioPendiente sube = copiaDe(v);
            sube.setMaxHielo(hielo("WI4", 4));                    // de rank 2 a 4
            assertEquals("RELAJA", sentido(v, sube, "Hielo"),
                    "permitir hielo más difícil relaja el umbral");

            AccesoNivelCambioPendiente baja = copiaDe(v);
            baja.setMaxRoca(roca("II", 2));                       // de rank 3 a 2
            assertEquals("ENDURECE", sentido(v, baja, "Roca"));
        }

        @Test
        @DisplayName("un rank mayor relaja aunque la etiqueta ordene al contrario")
        void mandaElRankNoLaEtiqueta() {
            AccesoRutaPorNivel v = vigente();
            v.setMaxCiclismo(ciclo("S4", 1));                     // etiqueta alta, rank bajo
            AccesoNivelCambioPendiente c = copiaDe(v);
            c.setMaxCiclismo(ciclo("S0", 5));                     // etiqueta baja, rank alto

            assertEquals("RELAJA", sentido(v, c, "Ciclismo"),
                    "el orden lo da el rank; comparar el texto daría ENDURECE");
        }

        @Test
        @DisplayName("mismo rank con otra etiqueta → IGUAL")
        void mismoRank() {
            AccesoRutaPorNivel v = vigente();
            AccesoNivelCambioPendiente c = copiaDe(v);
            c.setMaxIfas(ifas("PD", 2));                          // otro id, mismo rank que F

            assertEquals("IGUAL", sentido(v, c, "IFAS"));
        }
    }

    // ── sin matriz vigente no se inventa una comparación ─────────────────────

    @Test
    @DisplayName("si el nivel no tiene matriz, no se compara nada en vez de asumir")
    void sinMatrizVigente() {
        AccesoNivelCambioPendiente c = copiaDe(vigente());
        Map<String, CambioEscala> e = escalasDe(null, c);

        assertTrue(e.isEmpty(),
                "sin matriz vigente no hay con qué comparar: mejor nada que un sentido inventado");
    }

    // ── quién puede aprobar ──────────────────────────────────────────────────

    @Test
    @DisplayName("quien lo pidió no puede aprobarlo, y la respuesta lo dice")
    void quienPideNoAprueba() {
        AccesoRutaPorNivel v = vigente();
        AccesoNivelCambioPendiente c = copiaDe(v);                // lo pidió OTRO

        when(cambioRepository.findPendientes()).thenReturn(List.of(c));
        when(accesoRepository.findByNivelSocioId(nivel.getId())).thenReturn(Optional.of(v));

        when(mountainService.findSocio(QUIEN)).thenReturn(socio(QUIEN));
        assertTrue(service.listarPendientes(QUIEN).get(0).puedoAprobar(),
                "otra persona sí puede aprobarlo");

        when(mountainService.findSocio(OTRO)).thenReturn(socio(OTRO));
        assertFalse(service.listarPendientes(OTRO).get(0).puedoAprobar(),
                "quien lo solicitó no puede ser el segundo aprobador");
    }
}
