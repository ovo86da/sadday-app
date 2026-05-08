package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.entity.*;

import com.sadday.app.mountains.repository.*;
import com.sadday.app.security.audit.AuditAspect.Auditable;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.salidas.repository.SalidaRepository;
import com.sadday.app.socios.repository.ClasificacionSocioRepository;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RutaService {

    private final RutaRepository                rutaRepository;

    private final EquipoMontanaRepository       equipoMontanaRepository;
    private final MountainService               mountainService;
    private final SocioRepository               socioRepository;
    private final ContactoService               contactoService;
    private final ClasificacionSocioRepository  clasifSocioRepository;
    private final DificultadSenderismoRepository senderismoRepository;
    private final SalidaRepository               salidaRepository;
    private final RutaDocumentoService           rutaDocumentoService;

    // =========================================================================
    // Listar / obtener
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<RutaSummaryResponse> listar(Integer mountainId, Boolean aprobada,
                                            String tipoActividad, String q, Pageable pageable) {
        return rutaRepository.findAll(buildSpec(mountainId, aprobada, tipoActividad, q), pageable)
                .map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public RutaResponse obtener(Integer id) {
        Ruta ruta = findById(id);
        List<ContactoRutaResponse> contactos = contactoService.listarContactosRuta(id);
        List<RutaDocumentoResponse> docs = rutaDocumentoService.listarPorRuta(id);
        return toResponse(ruta, contactos, docs);
    }

    // =========================================================================
    // Crear / actualizar / aprobar / eliminar
    // =========================================================================

    @Auditable(accion = "CREATE_RUTA", entidad = "rutas", idFromReturn = true, detalle = "Ruta creada")
    @PreAuthorize("isAuthenticated()")
    public RutaResponse crear(CreateRutaRequest req, UUID propuestaPorId) {
        validarRequest(req.tipoActividad(), req.mountainId(), req.lugarReferencia(),
                req.escalaAlpinaIfasId(), req.dificultadRocaId(), req.dificultadHieloId(),
                req.compromisoId(), req.yosemiteId(), req.saddayNivelTecnicoId(),
                req.saddayNivelFisicoId(), req.tipoEscalada(), req.dificultadSenderismoId(),
                req.tipoBicicleta());

        Mountain mountain = req.mountainId() != null ? mountainService.findById(req.mountainId()) : null;
        Socio propuestaPor = findSocio(propuestaPorId);
        ClasificacionSocio nivelMinimo = req.nivelMinimoSocioId() != null
                ? findClasifSocio(req.nivelMinimoSocioId()) : null;

        Ruta ruta = Ruta.builder()
                .nombre(req.nombre())
                .tipoActividad(req.tipoActividad())
                .mountain(mountain)
                .lugarReferencia(req.lugarReferencia())
                .sectorZona(req.sectorZona())
                .longitudKm(req.longitudKm())
                .desnivelM(req.desnivelM())
                .duracionDias(req.duracionDias())
                .duracionHoras(req.duracionHoras())
                .peligrosNotas(req.peligrosNotas())
                .requierePermisos(req.requierePermisos())
                .documentacionUrl(req.documentacionUrl())
                .trackUrl(req.trackUrl())
                .nivelMinimoSocio(nivelMinimo)
                .propuestaPor(propuestaPor)
                .build();

        Ruta saved = rutaRepository.save(ruta);
        guardarSubTipo(saved, req);

        log.info("Ruta creada: id={}, tipo={}, nombre={}", saved.getId(), saved.getTipoActividad(), saved.getNombre());
        return toResponse(rutaRepository.findById(saved.getId()).orElseThrow(), List.of(), List.of());
    }

    @Auditable(accion = "UPDATE_RUTA", entidad = "rutas", idArgName = "id", detalle = "Datos de la ruta actualizados")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public RutaResponse actualizar(Integer id, UpdateRutaRequest req) {
        validarRequest(req.tipoActividad(), req.mountainId(), req.lugarReferencia(),
                req.escalaAlpinaIfasId(), req.dificultadRocaId(), req.dificultadHieloId(),
                req.compromisoId(), req.yosemiteId(), req.saddayNivelTecnicoId(),
                req.saddayNivelFisicoId(), req.tipoEscalada(), req.dificultadSenderismoId(),
                req.tipoBicicleta());

        Ruta ruta = findById(id);
        Mountain mountain = req.mountainId() != null ? mountainService.findById(req.mountainId()) : null;
        ClasificacionSocio nivelMinimo = req.nivelMinimoSocioId() != null
                ? findClasifSocio(req.nivelMinimoSocioId()) : null;

        ruta.setNombre(req.nombre());
        ruta.setTipoActividad(req.tipoActividad());
        ruta.setMountain(mountain);
        ruta.setLugarReferencia(req.lugarReferencia());
        ruta.setSectorZona(req.sectorZona());
        ruta.setLongitudKm(req.longitudKm());
        ruta.setDesnivelM(req.desnivelM());
        ruta.setDuracionDias(req.duracionDias());
        ruta.setDuracionHoras(req.duracionHoras());
        ruta.setPeligrosNotas(req.peligrosNotas());
        ruta.setRequierePermisos(req.requierePermisos());
        ruta.setDocumentacionUrl(req.documentacionUrl());
        ruta.setTrackUrl(req.trackUrl());
        ruta.setNivelMinimoSocio(nivelMinimo);

        actualizarSubTipo(ruta, req);

        List<ContactoRutaResponse> contactos = contactoService.listarContactosRuta(id);
        List<RutaDocumentoResponse> docs = rutaDocumentoService.listarPorRuta(id);
        return toResponse(rutaRepository.save(ruta), contactos, docs);
    }

    @Auditable(accion = "APROBAR_RUTA", entidad = "rutas", idArgName = "id", detalle = "Ruta aprobada para salidas")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public void aprobar(Integer id, UUID aprobadaPorId) {
        Ruta ruta = findById(id);
        ruta.setAprobada(true);
        ruta.setAprobadaPor(findSocio(aprobadaPorId));
        ruta.setAprobadaEn(LocalDateTime.now());
        log.info("Ruta aprobada: id={}, aprobada_por={}", id, aprobadaPorId);
    }

    @Auditable(accion = "DELETE_RUTA", entidad = "rutas", idArgName = "id", detalle = "Ruta eliminada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public void eliminar(Integer id) {
        Ruta ruta = findById(id);
        if (salidaRepository.existsByRutaIdAndEliminadaFalse(id)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "No se puede eliminar la ruta porque tiene salidas asociadas.");
        }
        rutaRepository.delete(ruta);
        log.info("Ruta eliminada: id={}", id);
    }

    // =========================================================================
    // Helpers — sub-tipo
    // =========================================================================

    private void guardarSubTipo(Ruta ruta, CreateRutaRequest req) {
        switch (req.tipoActividad()) {
            case ALPINISMO -> {
                RutaAlpinismo a = RutaAlpinismo.builder()
                        .ruta(ruta)
                        .escalaAlpinaIfas(mountainService.findEscala(req.escalaAlpinaIfasId()))
                        .dificultadRoca(mountainService.findRoca(req.dificultadRocaId()))
                        .dificultadHielo(mountainService.findHielo(req.dificultadHieloId()))
                        .compromiso(mountainService.findCompromiso(req.compromisoId()))
                        .yosemite(mountainService.findYosemite(req.yosemiteId()))
                        .saddayNivelTecnico(mountainService.findSadday(req.saddayNivelTecnicoId()))
                        .saddayNivelFisico(mountainService.findSadday(req.saddayNivelFisicoId()))
                        .equipoMontana(req.equipoMontanaId() != null
                                ? equipoMontanaRepository.findById(req.equipoMontanaId()).orElse(null) : null)
                        .build();
                ruta.setAlpinismo(a);
            }
            case ESCALADA -> {
                RutaEscalada e = RutaEscalada.builder()
                        .ruta(ruta)
                        .dificultadRoca(mountainService.findRoca(req.dificultadRocaId()))
                        .tipoEscalada(req.tipoEscalada())
                        .numCintas(req.numCintas())
                        .alturaViaM(req.alturaViaM())
                        .tipoRoca(req.tipoRoca())
                        .build();
                ruta.setEscalada(e);
            }
            case TREKKING -> {
                RutaTrekking t = RutaTrekking.builder()
                        .ruta(ruta)
                        .dificultad(findSenderismo(req.dificultadSenderismoId()))
                        .esCircular(req.esCircular() != null ? req.esCircular() : false)
                        .fuentesAgua(req.fuentesAgua() != null ? req.fuentesAgua() : false)
                        .tipoTerreno(req.tipoTerreno())
                        .build();
                ruta.setTrekking(t);
            }
            case CICLISMO -> {
                RutaCiclismo c = RutaCiclismo.builder()
                        .ruta(ruta)
                        .tipoBicicleta(req.tipoBicicleta())
                        .dificultadTecnica(req.dificultadTecnicaCiclismo())
                        .superficiePredominante(req.superficiePredominante())
                        .ciclabilidadPct(req.ciclabilidadPct())
                        .build();
                ruta.setCiclismo(c);
            }
        }
    }

    private void actualizarSubTipo(Ruta ruta, UpdateRutaRequest req) {
        switch (req.tipoActividad()) {
            case ALPINISMO -> {
                RutaAlpinismo a = ruta.getAlpinismo() != null
                        ? ruta.getAlpinismo()
                        : new RutaAlpinismo();
                a.setRuta(ruta);
                a.setEscalaAlpinaIfas(mountainService.findEscala(req.escalaAlpinaIfasId()));
                a.setDificultadRoca(mountainService.findRoca(req.dificultadRocaId()));
                a.setDificultadHielo(mountainService.findHielo(req.dificultadHieloId()));
                a.setCompromiso(mountainService.findCompromiso(req.compromisoId()));
                a.setYosemite(mountainService.findYosemite(req.yosemiteId()));
                a.setSaddayNivelTecnico(mountainService.findSadday(req.saddayNivelTecnicoId()));
                a.setSaddayNivelFisico(mountainService.findSadday(req.saddayNivelFisicoId()));
                a.setEquipoMontana(req.equipoMontanaId() != null
                        ? equipoMontanaRepository.findById(req.equipoMontanaId()).orElse(null) : null);
                ruta.setAlpinismo(a);
                ruta.setEscalada(null);
                ruta.setTrekking(null);
                ruta.setCiclismo(null);
            }
            case ESCALADA -> {
                RutaEscalada e = ruta.getEscalada() != null ? ruta.getEscalada() : new RutaEscalada();
                e.setRuta(ruta);
                e.setDificultadRoca(mountainService.findRoca(req.dificultadRocaId()));
                e.setTipoEscalada(req.tipoEscalada());
                e.setNumCintas(req.numCintas());
                e.setAlturaViaM(req.alturaViaM());
                e.setTipoRoca(req.tipoRoca());
                ruta.setEscalada(e);
                ruta.setAlpinismo(null);
                ruta.setTrekking(null);
                ruta.setCiclismo(null);
            }
            case TREKKING -> {
                RutaTrekking t = ruta.getTrekking() != null ? ruta.getTrekking() : new RutaTrekking();
                t.setRuta(ruta);
                t.setDificultad(findSenderismo(req.dificultadSenderismoId()));
                t.setEsCircular(req.esCircular() != null ? req.esCircular() : false);
                t.setFuentesAgua(req.fuentesAgua() != null ? req.fuentesAgua() : false);
                t.setTipoTerreno(req.tipoTerreno());
                ruta.setTrekking(t);
                ruta.setAlpinismo(null);
                ruta.setEscalada(null);
                ruta.setCiclismo(null);
            }
            case CICLISMO -> {
                RutaCiclismo c = ruta.getCiclismo() != null ? ruta.getCiclismo() : new RutaCiclismo();
                c.setRuta(ruta);
                c.setTipoBicicleta(req.tipoBicicleta());
                c.setDificultadTecnica(req.dificultadTecnicaCiclismo());
                c.setSuperficiePredominante(req.superficiePredominante());
                c.setCiclabilidadPct(req.ciclabilidadPct());
                ruta.setCiclismo(c);
                ruta.setAlpinismo(null);
                ruta.setEscalada(null);
                ruta.setTrekking(null);
            }
        }
    }

    // =========================================================================
    // Validación de campos requeridos según tipo
    // =========================================================================

    private void validarRequest(TipoActividad tipo, Integer mountainId, String lugarReferencia,
                                 String ifasId, String rocaId, String hieloId, String compromisoId,
                                 String yosemiteId, String saddayTecId, String saddayFisId,
                                 String tipoEscalada, String senderismoId, String tipoBicicleta) {
        if (mountainId == null && (lugarReferencia == null || lugarReferencia.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Debe indicar una montaña o un lugar de referencia.");
        }
        switch (tipo) {
            case ALPINISMO -> {
                requireField(ifasId,     "escalaAlpinaIfasId");
                requireField(rocaId,     "dificultadRocaId");
                requireField(hieloId,    "dificultadHieloId");
                requireField(compromisoId, "compromisoId");
                requireField(yosemiteId, "yosemiteId");
                requireField(saddayTecId,"saddayNivelTecnicoId");
                requireField(saddayFisId,"saddayNivelFisicoId");
            }
            case ESCALADA -> {
                requireField(rocaId,      "dificultadRocaId");
                requireField(tipoEscalada,"tipoEscalada");
            }
            case TREKKING -> requireField(senderismoId, "dificultadSenderismoId");
            case CICLISMO  -> requireField(tipoBicicleta, "tipoBicicleta");
        }
    }

    private void requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El campo '" + fieldName + "' es requerido para este tipo de actividad.");
        }
    }

    // =========================================================================
    // Helpers — finders
    // =========================================================================

    private Ruta findById(Integer id) {
        return rutaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_NOT_FOUND));
    }

    private Socio findSocio(UUID id) {
        return socioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
    }

    private ClasificacionSocio findClasifSocio(String id) {
        return clasifSocioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Clasificación de socio no encontrada: " + id));
    }

    private DificultadSenderismo findSenderismo(String id) {
        return senderismoRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Dificultad de senderismo no encontrada: " + id));
    }

    /** Expuesto para uso del PlanificadorService. */
    public Ruta findRutaById(Integer id) { return findById(id); }

    public List<EquipoMontana> listarEquipos() {
        return equipoMontanaRepository.findAll();
    }

    // =========================================================================
    // Specification
    // =========================================================================

    private Specification<Ruta> buildSpec(Integer mountainId, Boolean aprobada,
                                           String tipoActividad, String q) {
        Specification<Ruta> spec = (root, query, cb) -> cb.conjunction();

        if (mountainId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("mountain").get("id"), mountainId));
        }
        if (aprobada != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("aprobada"), aprobada));
        }
        if (tipoActividad != null && !tipoActividad.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("tipoActividad"), TipoActividad.valueOf(tipoActividad.toUpperCase())));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombre")), pattern),
                    cb.like(cb.lower(root.get("sectorZona")), pattern),
                    cb.like(cb.lower(root.get("lugarReferencia")), pattern)
            ));
        }
        return spec;
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private RutaResponse toResponse(Ruta r, List<ContactoRutaResponse> contactos,
                                     List<RutaDocumentoResponse> documentosPermiso) {
        return new RutaResponse(
                r.getId(),
                r.getNombre(),
                r.getTipoActividad().name(),
                r.getMountain() != null ? r.getMountain().getId() : null,
                r.getMountain() != null ? r.getMountain().getNombre() : null,
                r.getLugarReferencia(),
                r.getSectorZona(),
                r.getLongitudKm(),
                r.getDesnivelM(),
                r.getDuracionDias(),
                r.getDuracionHoras(),
                r.getPeligrosNotas(),
                r.getRequierePermisos(),
                r.getDocumentacionUrl(),
                r.getTrackUrl(),
                r.getNivelMinimoSocio() != null ? r.getNivelMinimoSocio().getId() : null,
                r.getNivelMinimoSocio() != null ? r.getNivelMinimoSocio().getNombre() : null,
                r.getAprobada(),
                r.getAprobadaPor() != null ? r.getAprobadaPor().getId() : null,
                r.getAprobadaEn(),
                r.getPropuestaPor() != null ? r.getPropuestaPor().getId() : null,
                contactos,
                documentosPermiso,
                r.getCreatedAt(),
                r.getUpdatedAt(),
                buildAlpinismoDetail(r.getAlpinismo()),
                buildEscaladaDetail(r.getEscalada()),
                buildTrekkingDetail(r.getTrekking()),
                buildCiclismoDetail(r.getCiclismo())
        );
    }

    private RutaSummaryResponse toSummaryResponse(Ruta r) {
        return new RutaSummaryResponse(
                r.getId(),
                r.getNombre(),
                r.getTipoActividad().name(),
                r.getMountain() != null ? r.getMountain().getId() : null,
                r.getMountain() != null ? r.getMountain().getNombre() : null,
                r.getLugarReferencia(),
                r.getSectorZona(),
                r.getLongitudKm(),
                r.getDesnivelM(),
                r.getDuracionDias(),
                r.getDuracionHoras(),
                r.getRequierePermisos(),
                r.getTrackUrl(),
                r.getNivelMinimoSocio() != null ? r.getNivelMinimoSocio().getId() : null,
                r.getNivelMinimoSocio() != null ? r.getNivelMinimoSocio().getNombre() : null,
                r.getAprobada(),
                r.getPropuestaPor() != null ? r.getPropuestaPor().getId() : null,
                r.getCreatedAt(),
                buildDificultadResumen(r)
        );
    }

    private String buildDificultadResumen(Ruta r) {
        return switch (r.getTipoActividad()) {
            case ALPINISMO -> r.getAlpinismo() != null
                    ? r.getAlpinismo().getEscalaAlpinaIfas().getGrado()
                      + " / " + r.getAlpinismo().getDificultadHielo().getGrado()
                    : "";
            case ESCALADA -> r.getEscalada() != null
                    ? r.getEscalada().getDificultadRoca().getFrancesa()
                      + " " + r.getEscalada().getTipoEscalada()
                    : "";
            case TREKKING -> r.getTrekking() != null
                    ? r.getTrekking().getDificultad().getNombre()
                    : "";
            case CICLISMO -> r.getCiclismo() != null
                    ? r.getCiclismo().getTipoBicicleta()
                      + (r.getCiclismo().getDificultadTecnica() != null
                         ? " " + r.getCiclismo().getDificultadTecnica() : "")
                    : "";
        };
    }

    private RutaResponse.AlpinismoDetail buildAlpinismoDetail(RutaAlpinismo a) {
        if (a == null) return null;
        return new RutaResponse.AlpinismoDetail(
                a.getEscalaAlpinaIfas().getId(),   a.getEscalaAlpinaIfas().getGrado(),
                a.getDificultadRoca().getId(),      a.getDificultadRoca().getUiaa(),
                a.getDificultadHielo().getId(),     a.getDificultadHielo().getGrado(),
                a.getCompromiso().getId(),           a.getCompromiso().getTipo(),
                a.getYosemite().getId(),             a.getYosemite().getTipo(),
                a.getSaddayNivelTecnico().getId(),  a.getSaddayNivelTecnico().getEscala(),
                a.getSaddayNivelFisico().getId(),   a.getSaddayNivelFisico().getEscala(),
                a.getEquipoMontana() != null ? a.getEquipoMontana().getId() : null,
                a.getEquipoMontana() != null ? a.getEquipoMontana().getNombre() : null
        );
    }

    private RutaResponse.EscaladaDetail buildEscaladaDetail(RutaEscalada e) {
        if (e == null) return null;
        return new RutaResponse.EscaladaDetail(
                e.getDificultadRoca().getId(), e.getDificultadRoca().getUiaa(),
                e.getTipoEscalada(), e.getNumCintas(), e.getAlturaViaM(), e.getTipoRoca()
        );
    }

    private RutaResponse.TrekkingDetail buildTrekkingDetail(RutaTrekking t) {
        if (t == null) return null;
        return new RutaResponse.TrekkingDetail(
                t.getDificultad().getId(), t.getDificultad().getNombre(),
                t.getEsCircular(), t.getFuentesAgua(), t.getTipoTerreno()
        );
    }

    private RutaResponse.CiclismoDetail buildCiclismoDetail(RutaCiclismo c) {
        if (c == null) return null;
        return new RutaResponse.CiclismoDetail(
                c.getTipoBicicleta(), c.getDificultadTecnica(),
                c.getSuperficiePredominante(), c.getCiclabilidadPct()
        );
    }
}
