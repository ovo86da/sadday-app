package com.sadday.app.planificador.service;

import com.sadday.app.informes.entity.InformeSalida;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.service.RutaService;
import com.sadday.app.planificador.dto.RecomendacionResponse;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Genera recomendaciones para planificar una salida.
 *
 * <p>Combina datos estáticos de la ruta con estadísticas agregadas
 * del historial de informes de esa ruta. Accesible por todos los socios autenticados.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanificadorService {

    private static final int MIN_SALIDAS_FIABLES = 3;

    private final RutaService              rutaService;
    private final InformeSalidaRepository  informeRepository;

    @PreAuthorize("isAuthenticated()")
    public RecomendacionResponse recomendar(Integer rutaId) {
        Ruta ruta = rutaService.findRutaById(rutaId);
        if (!Boolean.TRUE.equals(ruta.getAprobada())) {
            throw new BusinessException(ErrorCode.RUTA_NOT_APPROVED);
        }

        List<InformeSalida> informes = informeRepository.findBySalidaRutaId(rutaId);
        int total = informes.size();
        boolean insuficientes = total < MIN_SALIDAS_FIABLES;

        // ── Tasa de éxito ────────────────────────────────────────────────────
        Double tasaExito = total == 0 ? null
                : round2((double) informes.stream().filter(InformeSalida::getSeRealizo).count() / total * 100);

        // ── Hora promedio de salida del club ─────────────────────────────────
        String horaPromedio = calcularHoraPromedio(informes);

        // ── Transporte ───────────────────────────────────────────────────────
        long conTransporte = informes.stream().filter(InformeSalida::alquiloAlgunTransporte).count();
        Double pctTransporte = total == 0 ? null : round2((double) conTransporte / total * 100);
        Double costoTransporte = promedioCosto(
                informes.stream()
                        .filter(InformeSalida::alquiloAlgunTransporte)
                        .flatMap(i -> i.getSegmentos().stream()
                                .filter(s -> Boolean.TRUE.equals(s.getAlquiloTransporte())
                                        && s.getCostoIndividual() != null)
                                .map(s -> s.getCostoIndividual()))
                        .toList()
        );

        // ── Guía ─────────────────────────────────────────────────────────────
        long conGuia = informes.stream().filter(i -> Boolean.TRUE.equals(i.getAlquiloGuia())).count();
        Double pctGuia = total == 0 ? null : round2((double) conGuia / total * 100);
        Double costoGuia = promedioCosto(
                informes.stream()
                        .filter(i -> Boolean.TRUE.equals(i.getAlquiloGuia()) && i.getCostoGuia() != null)
                        .map(InformeSalida::getCostoGuia)
                        .toList()
        );

        // ── Costo total promedio ─────────────────────────────────────────────
        Double costoTotal = promedioCosto(
                informes.stream()
                        .filter(i -> i.getCostoTotal() != null)
                        .map(InformeSalida::getCostoTotal)
                        .toList()
        );

        var alp = ruta.getAlpinismo();
        var esc = ruta.getEscalada();
        var trk = ruta.getTrekking();
        var cic = ruta.getCiclismo();

        return new RecomendacionResponse(
                ruta.getId(),
                ruta.getNombre(),
                ruta.getTipoActividad(),
                ruta.getMountain() != null ? ruta.getMountain().getNombre() : ruta.getLugarReferencia(),
                ruta.getSectorZona(),
                ruta.getRequierePermisos(),
                ruta.getTrackUrl(),

                // Alpinismo
                alp != null ? alp.getSaddayNivelTecnico().getEscala() : null,
                alp != null ? alp.getSaddayNivelFisico().getEscala() : null,
                alp != null ? alp.getEscalaAlpinaIfas().getGrado() : null,
                alp != null ? alp.getDificultadRoca().getUiaa() : null,
                alp != null ? alp.getDificultadHielo().getGrado() : null,
                alp != null && alp.getEquipoMontana() != null ? alp.getEquipoMontana().getId() : null,
                alp != null && alp.getEquipoMontana() != null ? alp.getEquipoMontana().getNombre() : null,

                // Escalada
                esc != null ? esc.getDificultadRoca().getUiaa() : null,
                esc != null ? esc.getTipoEscalada() : null,
                esc != null && esc.getNumCintas() != null ? esc.getNumCintas().intValue() : null,
                esc != null ? esc.getAlturaViaM() : null,
                esc != null ? esc.getTipoRoca() : null,

                // Trekking
                trk != null ? trk.getDificultad().getNombre() : null,
                trk != null ? trk.getEsCircular() : null,
                trk != null ? trk.getFuentesAgua() : null,
                trk != null ? trk.getTipoTerreno() : null,

                // Ciclismo
                cic != null ? cic.getTipoBicicleta() : null,
                cic != null ? cic.getDificultadTecnica() : null,
                cic != null ? cic.getSuperficiePredominante() : null,

                total,
                insuficientes,
                tasaExito,
                horaPromedio,
                pctTransporte,
                costoTransporte,
                pctGuia,
                costoGuia,
                costoTotal
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Promedia una lista de horas locales convirtiéndolas a minutos desde medianoche.
     * Retorna "HH:mm" o null si la lista está vacía.
     */
    private String calcularHoraPromedio(List<InformeSalida> informes) {
        List<LocalTime> horas = informes.stream()
                .filter(i -> i.getHoraSalidaClub() != null)
                .map(InformeSalida::getHoraSalidaClub)
                .toList();
        if (horas.isEmpty()) return null;

        int totalMinutos = horas.stream()
                .mapToInt(h -> h.getHour() * 60 + h.getMinute())
                .sum();
        int promedioMinutos = totalMinutos / horas.size();
        return String.format("%02d:%02d", promedioMinutos / 60, promedioMinutos % 60);
    }

    private Double promedioCosto(List<BigDecimal> costos) {
        if (costos.isEmpty()) return null;
        double sum = costos.stream().mapToDouble(BigDecimal::doubleValue).sum();
        return round2(sum / costos.size());
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
