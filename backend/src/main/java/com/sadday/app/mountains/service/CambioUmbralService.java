package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.CambioUmbralResponse;
import com.sadday.app.mountains.dto.CambioUmbralResponse.CambioEscala;
import com.sadday.app.mountains.dto.UpdateAccesoNivelRequest;
import com.sadday.app.mountains.entity.*;
import com.sadday.app.mountains.entity.AccesoNivelCambioPendiente.Estado;
import com.sadday.app.mountains.repository.AccesoNivelCambioPendienteRepository;
import com.sadday.app.mountains.repository.AccesoRutaPorNivelRepository;
import com.sadday.app.security.audit.AuditAspect.Auditable;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cambios de umbrales de acceso que esperan una segunda aprobación (FR-026).
 *
 * <p>Cambiar {@link AccesoRutaPorNivel} decide a qué dificultad puede
 * inscribirse cada nivel de socio. Un directivo lo pide, otro lo aprueba, y
 * hasta entonces <strong>el umbral viejo sigue vigente</strong>: un umbral
 * relajado sin aprobar es justo el riesgo que esto evita.
 *
 * <p>Endurecer pasa por lo mismo que relajar, por decisión explícita del club.
 * Tiene una consecuencia conocida y aceptada: arreglar un umbral peligrosamente
 * laxo también espera a una segunda persona.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CambioUmbralService {

    private final AccesoNivelCambioPendienteRepository cambioRepository;
    private final AccesoRutaPorNivelRepository accesoRepository;
    private final MountainService mountainService;
    private final CambioUmbralMailSender mailSender;

    // =========================================================================
    // El único punto de decisión
    // =========================================================================

    /**
     * Si {@code socio} puede resolver {@code cambio}.
     *
     * <p><strong>Esta es la regla que va a cambiar.</strong> Hoy es «cualquier
     * directivo o admin que no sea quien lo pidió». Si mañana el club quiere
     * que solo aprueben el jefe o el subjefe de montaña, se cambia aquí y en
     * ningún otro sitio: ni el flujo, ni las pantallas, ni la base de datos
     * saben quién puede aprobar.
     *
     * <p>Por lo mismo, la fila del cambio guarda <em>quién</em> lo resolvió y
     * nunca <em>qué cargo</em> podía hacerlo: así cambiar la regla no obliga a
     * migrar las filas viejas.
     */
    public boolean puedeAprobar(Socio socio, AccesoNivelCambioPendiente cambio) {
        if (socio == null || cambio == null || !cambio.estaPendiente()) return false;
        // Quien lo pidió no se lo aprueba a sí mismo: ahí está toda la gracia.
        return !socio.getId().equals(cambio.getSolicitadoPor().getId());
    }

    // =========================================================================
    // Pedir, aprobar, rechazar, cancelar
    // =========================================================================

    /**
     * Deja el cambio esperando. <strong>No toca la tabla vigente.</strong>
     */
    @Auditable(accion = "SOLICITAR_CAMBIO_UMBRAL", entidad = "acceso_nivel_cambio_pendiente",
               detalle = "Cambio de umbrales de acceso solicitado, pendiente de aprobación")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public CambioUmbralResponse solicitar(String nivelSocioId, UpdateAccesoNivelRequest req,
                                          UUID solicitadoPorId) {
        AccesoRutaPorNivel vigente = accesoRepository.findByNivelSocioId(nivelSocioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Nivel de socio no encontrado: " + nivelSocioId));

        Socio solicitante = mountainService.findSocio(solicitadoPorId);

        AccesoNivelCambioPendiente cambio = AccesoNivelCambioPendiente.builder()
                .nivelSocio(vigente.getNivelSocio())
                .maxIfas(mountainService.findEscala(req.maxIfasId()))
                .maxRoca(mountainService.findRoca(req.maxRocaId()))
                .maxHielo(mountainService.findHielo(req.maxHieloId()))
                .maxCompromiso(mountainService.findCompromiso(req.maxCompromisoId()))
                .maxYosemite(mountainService.findYosemite(req.maxYosemiteId()))
                .maxSaddayTecnico(mountainService.findSadday(req.maxSaddayTecnicoId()))
                .maxSaddayFisico(mountainService.findSadday(req.maxSaddayFisicoId()))
                .solicitadoPor(solicitante)
                .solicitadoEn(LocalDateTime.now())
                .estado(Estado.PENDIENTE)
                .build();

        if (sinCambios(vigente, cambio)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Los valores propuestos son los que ya están vigentes.");
        }

        try {
            cambio = cambioRepository.saveAndFlush(cambio);
        } catch (DataIntegrityViolationException e) {
            // Lo impide un índice único parcial en la base, no una comprobación
            // en Java: dos peticiones a la vez se saltarían un `if`.
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "Ya hay un cambio esperando aprobación para este nivel.");
        }

        log.info("Cambio de umbral solicitado: nivel={}, por={}", nivelSocioId, solicitadoPorId);
        mailSender.avisarSolicitud(cambio);
        return toResponse(cambio, vigente, solicitante);
    }

    @Auditable(accion = "APROBAR_CAMBIO_UMBRAL", entidad = "acceso_ruta_por_nivel",
               detalle = "Cambio de umbrales de acceso aprobado y aplicado")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public CambioUmbralResponse aprobar(UUID cambioId, UUID aprobadoPorId) {
        AccesoNivelCambioPendiente cambio = buscarPendiente(cambioId);
        Socio quien = mountainService.findSocio(aprobadoPorId);

        if (!puedeAprobar(quien, cambio)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Quien solicita un cambio de umbral no puede aprobarlo.");
        }

        AccesoRutaPorNivel vigente = accesoRepository
                .findByNivelSocioId(cambio.getNivelSocio().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        vigente.setMaxIfas(cambio.getMaxIfas());
        vigente.setMaxRoca(cambio.getMaxRoca());
        vigente.setMaxHielo(cambio.getMaxHielo());
        vigente.setMaxCompromiso(cambio.getMaxCompromiso());
        vigente.setMaxYosemite(cambio.getMaxYosemite());
        vigente.setMaxSaddayTecnico(cambio.getMaxSaddayTecnico());
        vigente.setMaxSaddayFisico(cambio.getMaxSaddayFisico());
        vigente.setUpdatedAt(LocalDateTime.now());
        vigente.setUpdatedBy(quien);
        accesoRepository.save(vigente);

        cambio.setEstado(Estado.APROBADO);
        cambio.setResueltoPor(quien);
        cambio.setResueltoEn(LocalDateTime.now());

        log.info("Cambio de umbral aprobado: id={}, nivel={}, por={}",
                cambioId, cambio.getNivelSocio().getId(), aprobadoPorId);
        mailSender.avisarResolucion(cambio);
        return toResponse(cambio, vigente, quien);
    }

    @Auditable(accion = "RECHAZAR_CAMBIO_UMBRAL", entidad = "acceso_nivel_cambio_pendiente",
               detalle = "Cambio de umbrales de acceso rechazado")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public CambioUmbralResponse rechazar(UUID cambioId, UUID rechazadoPorId, String motivo) {
        AccesoNivelCambioPendiente cambio = buscarPendiente(cambioId);
        Socio quien = mountainService.findSocio(rechazadoPorId);

        if (!puedeAprobar(quien, cambio)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Quien solicita un cambio de umbral no puede rechazarlo.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Hay que decir por qué se rechaza.");
        }

        cambio.setEstado(Estado.RECHAZADO);
        cambio.setResueltoPor(quien);
        cambio.setResueltoEn(LocalDateTime.now());
        cambio.setMotivoRechazo(motivo.trim());

        log.info("Cambio de umbral rechazado: id={}, por={}", cambioId, rechazadoPorId);
        mailSender.avisarResolucion(cambio);
        return toResponse(cambio, vigenteDe(cambio), quien);
    }

    /** Lo cancela quien lo pidió, y solo él. */
    @Auditable(accion = "CANCELAR_CAMBIO_UMBRAL", entidad = "acceso_nivel_cambio_pendiente",
               detalle = "Cambio de umbrales de acceso cancelado por quien lo solicitó")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public CambioUmbralResponse cancelar(UUID cambioId, UUID socioId) {
        AccesoNivelCambioPendiente cambio = buscarPendiente(cambioId);
        if (!cambio.getSolicitadoPor().getId().equals(socioId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Solo quien pidió el cambio puede cancelarlo.");
        }
        Socio quien = mountainService.findSocio(socioId);
        cambio.setEstado(Estado.CANCELADO);
        cambio.setResueltoPor(quien);
        cambio.setResueltoEn(LocalDateTime.now());

        log.info("Cambio de umbral cancelado: id={}", cambioId);
        return toResponse(cambio, vigenteDe(cambio), quien);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public List<CambioUmbralResponse> listarPendientes(UUID consultaPorId) {
        Socio quien = mountainService.findSocio(consultaPorId);
        return cambioRepository.findPendientes().stream()
                .map(c -> toResponse(c, vigenteDe(c), quien))
                .toList();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AccesoNivelCambioPendiente buscarPendiente(UUID id) {
        AccesoNivelCambioPendiente c = cambioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Cambio no encontrado"));
        if (!c.estaPendiente()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "Este cambio ya está " + c.getEstado().name().toLowerCase() + ".");
        }
        return c;
    }

    private AccesoRutaPorNivel vigenteDe(AccesoNivelCambioPendiente c) {
        return accesoRepository.findByNivelSocioId(c.getNivelSocio().getId()).orElse(null);
    }

    private boolean sinCambios(AccesoRutaPorNivel v, AccesoNivelCambioPendiente c) {
        return v.getMaxIfas().getId().equals(c.getMaxIfas().getId())
            && v.getMaxRoca().getId().equals(c.getMaxRoca().getId())
            && v.getMaxHielo().getId().equals(c.getMaxHielo().getId())
            && v.getMaxCompromiso().getId().equals(c.getMaxCompromiso().getId())
            && v.getMaxYosemite().getId().equals(c.getMaxYosemite().getId())
            && v.getMaxSaddayTecnico().getId().equals(c.getMaxSaddayTecnico().getId())
            && v.getMaxSaddayFisico().getId().equals(c.getMaxSaddayFisico().getId());
    }

    /**
     * Compara una escala y dice si relaja, endurece o se queda igual.
     *
     * <p>Se calcula aquí y no en el cliente porque depende del {@code rank} de
     * cada escala, que el cliente no conoce. Y se muestra aunque el flujo de
     * aprobación sea el mismo para los dos sentidos: es la información que hace
     * falta para decidir, no una regla de permisos.
     */
    private <T> CambioEscala comparar(String nombre, T actual, T propuesto,
                                      Function<T, Short> rank, Function<T, String> etiqueta) {
        if (actual == null) {
            return new CambioEscala(nombre, null, etiqueta.apply(propuesto), "IGUAL");
        }
        int ra = rank.apply(actual), rp = rank.apply(propuesto);
        String sentido = rp == ra ? "IGUAL" : (rp > ra ? "RELAJA" : "ENDURECE");
        return new CambioEscala(nombre, etiqueta.apply(actual), etiqueta.apply(propuesto), sentido);
    }

    private CambioUmbralResponse toResponse(AccesoNivelCambioPendiente c,
                                            AccesoRutaPorNivel v, Socio quienConsulta) {
        List<CambioEscala> escalas = v == null ? List.of() : List.of(
            comparar("IFAS", v.getMaxIfas(), c.getMaxIfas(),
                     EscalaAlpinaIfas::getRank, EscalaAlpinaIfas::getGrado),
            comparar("Roca", v.getMaxRoca(), c.getMaxRoca(),
                     DificultadRoca::getRank, DificultadRoca::getUiaa),
            comparar("Hielo", v.getMaxHielo(), c.getMaxHielo(),
                     DificultadHielo::getRank, DificultadHielo::getGrado),
            comparar("Compromiso", v.getMaxCompromiso(), c.getMaxCompromiso(),
                     Compromiso::getRank, Compromiso::getTipo),
            comparar("Yosemite", v.getMaxYosemite(), c.getMaxYosemite(),
                     SistemaClasesYosemite::getRank, SistemaClasesYosemite::getTipo),
            comparar("Técnico Sadday", v.getMaxSaddayTecnico(), c.getMaxSaddayTecnico(),
                     SaddayRiesgoExigencia::getRank, SaddayRiesgoExigencia::getEscala),
            comparar("Físico Sadday", v.getMaxSaddayFisico(), c.getMaxSaddayFisico(),
                     SaddayRiesgoExigencia::getRank, SaddayRiesgoExigencia::getEscala)
        );

        return new CambioUmbralResponse(
                c.getId(),
                c.getNivelSocio().getId(),
                c.getNivelSocio().getNombre(),
                c.getSolicitadoPor().getNombre() + " " + c.getSolicitadoPor().getApellido(),
                c.getSolicitadoEn(),
                c.getEstado().name(),
                c.getResueltoPor() == null ? null
                        : c.getResueltoPor().getNombre() + " " + c.getResueltoPor().getApellido(),
                c.getResueltoEn(),
                c.getMotivoRechazo(),
                escalas,
                puedeAprobar(quienConsulta, c),
                c.estaPendiente() && quienConsulta != null
                        && quienConsulta.getId().equals(c.getSolicitadoPor().getId())
        );
    }
}
