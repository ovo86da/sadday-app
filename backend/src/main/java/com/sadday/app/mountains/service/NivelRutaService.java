package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.EvaluacionNivelResponse;
import com.sadday.app.mountains.dto.EvaluacionNivelResponse.Hallazgo;
import com.sadday.app.mountains.entity.*;
import com.sadday.app.mountains.repository.AccesoRutaPorNivelRepository;
import com.sadday.app.mountains.repository.DificultadCiclismoStcRepository;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.repository.ClasificacionSocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static com.sadday.app.mountains.dto.EvaluacionNivelResponse.EXCEDE;
import static com.sadday.app.mountains.dto.EvaluacionNivelResponse.SIN_COMPROBAR;

/**
 * Cruza la dificultad de una ruta con lo que permite el nivel de un socio.
 *
 * <p>Esto es lo que FR-028 venía a arreglar: la matriz {@code acceso_ruta_por_nivel}
 * describía con nueve escalas qué puede intentar cada nivel, y <b>no la leía
 * nadie</b>. La decisión de inscribir se tomaba comparando dos enteros —el nivel
 * del socio contra un «nivel mínimo» que alguien teclea a mano en la salida—, y
 * si quien la creaba se olvidaba de ponerlo, no había validación y nada lo decía.
 *
 * <h2>Lo que no se puede comprobar frena</h2>
 *
 * <p>No devuelve apto/no apto a secas: devuelve <b>por qué</b>. Y distingue dos
 * cosas que es tentador juntar:
 *
 * <ul>
 *   <li><b>EXCEDE</b> — la ruta pide más de lo que ese nivel permite. Se sabe, y
 *       se dice con los dos valores: «la ruta pide WI3 y su nivel llega a WI2».</li>
 *   <li><b>SIN_COMPROBAR</b> — falta el dato para decidir: el techo de esa escala
 *       está sin fijar, la ruta no dice su dificultad, o el socio no tiene nivel.
 *       <b>También frena.</b> «Sin fijar» no es «sin límite», y un hueco de datos
 *       que deja pasar a alguien es exactamente el fallo que esto evita.</li>
 * </ul>
 *
 * <p>Eso tiene un coste consciente: mientras la directiva no fije los máximos de
 * senderismo y ciclismo, toda inscripción a trekking o ciclismo necesitará las
 * dos firmas. Es la decisión tomada, y se prefiere ese roce a que el sistema diga
 * «apto» sobre algo que no ha mirado.
 *
 * <h2>Cada tipo de ruta trae lo suyo</h2>
 *
 * <p>Alpinismo guarda las siete escalas; escalada solo roca; trekking solo
 * senderismo; ciclismo su grado técnico. Las escalas que ese tipo no usa no se
 * comparan —no son un hueco, es que no aplican—. Una <b>integral</b> se clasifica
 * como alpinismo y se mide con sus siete escalas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NivelRutaService {

    private final AccesoRutaPorNivelRepository accesoRepository;
    private final ClasificacionSocioRepository clasificacionRepository;
    private final DificultadCiclismoStcRepository dificultadCiclismoRepo;

    /**
     * Evalúa si el nivel dado alcanza para la ruta dada.
     *
     * @param nivelSocio el nivel técnico del socio; null se trata como un hueco,
     *                   no como «da igual».
     */
    @Transactional(readOnly = true)
    public EvaluacionNivelResponse evaluar(Ruta ruta, ClasificacionSocio nivelSocio) {
        List<Hallazgo> hallazgos = new ArrayList<>();

        if (ruta == null) {
            return sinComprobar("La salida no tiene ruta asociada, no hay dificultad que comparar.");
        }
        if (nivelSocio == null) {
            return sinComprobar("El socio no tiene nivel técnico asignado.");
        }

        AccesoRutaPorNivel techo = accesoRepository.findByNivelSocioId(nivelSocio.getId()).orElse(null);
        if (techo == null) {
            return sinComprobar("El nivel «" + nivelSocio.getNombre()
                    + "» no tiene umbrales de acceso definidos.");
        }

        // Una integral se clasifica como alpinismo: se mide con sus siete escalas.
        TipoActividad tipo = ruta.getTipoActividad();
        switch (tipo) {
            case ALPINISMO, INTEGRAL -> compararAlpinismo(ruta, techo, hallazgos, tipo);
            case ESCALADA  -> compararEscalada(ruta, techo, hallazgos);
            case TREKKING  -> compararTrekking(ruta, techo, hallazgos);
            case CICLISMO  -> compararCiclismo(ruta, techo, hallazgos);
        }

        boolean apto = hallazgos.isEmpty();
        return new EvaluacionNivelResponse(apto, List.copyOf(hallazgos));
    }

    /**
     * El nivel más bajo cuya matriz permite esta ruta entera.
     *
     * <p>Sirve para proponer el mínimo de una salida en vez de que se teclee a
     * ciegas. Devuelve null cuando <b>ningún</b> nivel la permite o cuando falta
     * algún dato para saberlo: en los dos casos la respuesta honesta es «no lo sé»,
     * y quien crea la salida lo verá como tal en lugar de recibir un número
     * inventado.
     */
    @Transactional(readOnly = true)
    public ClasificacionSocio nivelMinimoDerivado(Ruta ruta) {
        return clasificacionRepository.findAll().stream()
                .filter(n -> n.getNivel() != null)
                .sorted(Comparator.comparing(ClasificacionSocio::getNivel))
                .filter(n -> evaluar(ruta, n).apto())
                .findFirst()
                .orElse(null);
    }

    // =========================================================================
    // Comparación por tipo
    // =========================================================================

    private void compararAlpinismo(Ruta ruta, AccesoRutaPorNivel techo,
                                   List<Hallazgo> out, TipoActividad tipo) {
        RutaAlpinismo a = ruta.getAlpinismo();
        if (a == null) {
            out.add(hueco(tipo == TipoActividad.INTEGRAL
                    ? "La integral no tiene su clasificación de alpinismo registrada."
                    : "La ruta no tiene sus dificultades de alpinismo registradas."));
            return;
        }
        comparar(out, "IFAS", a.getEscalaAlpinaIfas(), techo.getMaxIfas(),
                 EscalaAlpinaIfas::getRank, EscalaAlpinaIfas::getGrado);
        comparar(out, "Roca", a.getDificultadRoca(), techo.getMaxRoca(),
                 DificultadRoca::getRank, DificultadRoca::getUiaa);
        comparar(out, "Hielo", a.getDificultadHielo(), techo.getMaxHielo(),
                 DificultadHielo::getRank, DificultadHielo::getGrado);
        comparar(out, "Compromiso", a.getCompromiso(), techo.getMaxCompromiso(),
                 Compromiso::getRank, Compromiso::getTipo);
        comparar(out, "Yosemite", a.getYosemite(), techo.getMaxYosemite(),
                 SistemaClasesYosemite::getRank, SistemaClasesYosemite::getTipo);
        comparar(out, "Técnico Sadday", a.getSaddayNivelTecnico(), techo.getMaxSaddayTecnico(),
                 SaddayRiesgoExigencia::getRank, SaddayRiesgoExigencia::getEscala);
        comparar(out, "Físico Sadday", a.getSaddayNivelFisico(), techo.getMaxSaddayFisico(),
                 SaddayRiesgoExigencia::getRank, SaddayRiesgoExigencia::getEscala);
    }

    private void compararEscalada(Ruta ruta, AccesoRutaPorNivel techo, List<Hallazgo> out) {
        RutaEscalada e = ruta.getEscalada();
        if (e == null) {
            out.add(hueco("La ruta no tiene su dificultad de escalada registrada."));
            return;
        }
        // Solo roca: las otras seis escalas no aplican a una vía de escalada.
        comparar(out, "Roca", e.getDificultadRoca(), techo.getMaxRoca(),
                 DificultadRoca::getRank, DificultadRoca::getUiaa);
    }

    private void compararTrekking(Ruta ruta, AccesoRutaPorNivel techo, List<Hallazgo> out) {
        RutaTrekking t = ruta.getTrekking();
        if (t == null) {
            out.add(hueco("La ruta no tiene su dificultad de senderismo registrada."));
            return;
        }
        comparar(out, "Senderismo", t.getDificultad(), techo.getMaxSenderismo(),
                 DificultadSenderismo::getRank, DificultadSenderismo::getNombre);
    }

    private void compararCiclismo(Ruta ruta, AccesoRutaPorNivel techo, List<Hallazgo> out) {
        RutaCiclismo c = ruta.getCiclismo();
        if (c == null || c.getDificultadTecnica() == null || c.getDificultadTecnica().isBlank()) {
            out.add(hueco("La ruta no indica su dificultad técnica de ciclismo."));
            return;
        }
        // `rutas_ciclismo.dificultad_tecnica` guarda el grado como texto (S0..S4),
        // no como clave foránea. Los ids del catálogo son esos mismos valores.
        DificultadCiclismoStc deRuta = dificultadCiclismoRepo
                .findById(c.getDificultadTecnica()).orElse(null);
        if (deRuta == null) {
            out.add(hueco("La dificultad técnica de la ruta («" + c.getDificultadTecnica()
                    + "») no está en el catálogo de ciclismo."));
            return;
        }
        comparar(out, "Ciclismo", deRuta, techo.getMaxCiclismo(),
                 DificultadCiclismoStc::getRank, DificultadCiclismoStc::getNombre);
    }

    // =========================================================================
    // El núcleo: una escala
    // =========================================================================

    /**
     * Compara una escala y añade un hallazgo solo si hay algo que decir.
     *
     * <p>El orden lo da el {@code rank} del catálogo, nunca la etiqueta: «S10»
     * ordena antes que «S2» si se comparan como texto.
     */
    private <T> void comparar(List<Hallazgo> out, String escala, T exigeRuta, T permiteNivel,
                              Function<T, Short> rank, Function<T, String> etiqueta) {
        if (exigeRuta == null) {
            // El tipo de ruta usa esta escala pero la ruta no la tiene puesta.
            out.add(new Hallazgo(escala, null, permiteNivel == null ? null : etiqueta.apply(permiteNivel),
                    SIN_COMPROBAR, "La ruta no indica su dificultad de " + escala.toLowerCase() + "."));
            return;
        }
        String deRuta = etiqueta.apply(exigeRuta);

        if (permiteNivel == null) {
            out.add(new Hallazgo(escala, deRuta, null, SIN_COMPROBAR,
                    "El nivel no tiene fijado un máximo de " + escala.toLowerCase()
                    + ", así que no se puede comprobar si " + deRuta + " lo excede."));
            return;
        }
        String delNivel = etiqueta.apply(permiteNivel);

        if (rank.apply(exigeRuta) > rank.apply(permiteNivel)) {
            out.add(new Hallazgo(escala, deRuta, delNivel, EXCEDE,
                    "La ruta pide " + deRuta + " y el nivel del socio llega a " + delNivel + "."));
        }
        // Si da, no se dice nada: los hallazgos son lo que impide, no un informe.
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private EvaluacionNivelResponse sinComprobar(String detalle) {
        return new EvaluacionNivelResponse(false, List.of(hueco(detalle)));
    }

    private Hallazgo hueco(String detalle) {
        return new Hallazgo(null, null, null, SIN_COMPROBAR, detalle);
    }
}
