package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.entity.*;
import com.sadday.app.mountains.repository.*;
import com.sadday.app.security.audit.AuditAspect.Auditable;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;

import com.sadday.app.socios.entity.Socio;
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

/**
 * Servicio del módulo Montañas.
 *
 * <p>Gestiona montañas y la tabla {@code acceso_ruta_por_nivel}
 * (umbrales de dificultad por nivel de socio, editables por Directivos).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MountainService {

    private final MountainRepository            mountainRepository;
    private final RutaRepository               rutaRepository;
    private final AccesoRutaPorNivelRepository  accesoRepository;
    private final EscalaAlpinaIfasRepository    escalaRepo;
    private final DificultadRocaRepository      rocaRepo;
    private final DificultadHieloRepository     hieloRepo;
    private final CompromisoRepository          compromisoRepo;
    private final SistemaClasesYosemiteRepository yosemiteRepo;
    private final SaddayRiesgoExigenciaRepository saddayRepo;
    private final ClasificacionSocioRepository  clasifSocioRepo;
    private final SocioRepository               socioRepository;
    private final EquipoMontanaRepository           equipoRepo;
    private final DificultadSenderismoRepository    dificultadSenderismoRepo;

    // =========================================================================
    // Lookups
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public MountainLookupsResponse obtenerLookups() {
        var escalas = escalaRepo.findAllByOrderByRankAsc().stream()
                .map(e -> new MountainLookupsResponse.EscalaItem(e.getId(), e.getGrado(), e.getNombre(), e.getDescripcion(), e.getRank()))
                .toList();

        var rocas = rocaRepo.findAllByOrderByRankAsc().stream()
                .map(r -> new MountainLookupsResponse.DificultadRocaItem(r.getId(), r.getUiaa(), r.getFrancesa(), r.getDescripcion(), r.getRank()))
                .toList();

        var hielos = hieloRepo.findAllByOrderByRankAsc().stream()
                .map(h -> new MountainLookupsResponse.DificultadHieloItem(h.getId(), h.getGrado(), h.getDescripcion(), h.getRank()))
                .toList();

        var compromisos = compromisoRepo.findAllByOrderByRankAsc().stream()
                .map(c -> new MountainLookupsResponse.CompromisoItem(c.getId(), c.getTipo(), c.getDescripcion(), c.getRank()))
                .toList();

        var yosemite = yosemiteRepo.findAllByOrderByRankAsc().stream()
                .map(y -> new MountainLookupsResponse.YosemiteItem(y.getId(), y.getTipo(), y.getDescripcion(), y.getRank()))
                .toList();

        var sadday = saddayRepo.findAllByOrderByRankAsc().stream()
                .map(s -> new MountainLookupsResponse.SaddayItem(s.getId(), s.getValor(), s.getEscala(), s.getDescripcion(), s.getRank()))
                .toList();

        var clasifs = clasifSocioRepo.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(c -> c.getNivel().intValue()))
                .map(c -> new MountainLookupsResponse.ClasifSocioItem(c.getId(), c.getNivel(), c.getNombre(), c.getDescripcion()))
                .toList();

        var equipos = equipoRepo.findAll().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getNombre()))
                .map(e -> new MountainLookupsResponse.EquipoItem(e.getId(), e.getNombre(), e.getDescripcion()))
                .toList();

        var senderismo = dificultadSenderismoRepo.findAllByOrderByRankAsc().stream()
                .map(d -> new MountainLookupsResponse.DificultadSenderismoItem(d.getId(), d.getNombre(), d.getDescripcion(), d.getRank()))
                .toList();

        return new MountainLookupsResponse(escalas, rocas, hielos, compromisos, yosemite, sadday, clasifs, equipos, senderismo);
    }

    // =========================================================================
    // Mountains — CRUD
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<MountainSummaryResponse> listar(String q, String region, Pageable pageable) {
        return mountainRepository.findAll(buildSpec(q, region), pageable)
                .map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public MountainResponse obtener(Integer id) {
        return toResponse(findById(id));
    }

    @Auditable(accion = "CREATE_MOUNTAIN", entidad = "mountains", detalle = "Montaña creada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public MountainResponse crear(CreateMountainRequest request) {
        Mountain mountain = Mountain.builder()
                .nombre(request.nombre())
                .region(request.region())
                .altitud(request.altitud())
                .pais(request.pais())
                .build();
        Mountain saved = mountainRepository.save(mountain);
        log.info("Montaña creada: id={}, nombre={}", saved.getId(), saved.getNombre());
        return toResponse(saved);
    }

    @Auditable(accion = "UPDATE_MOUNTAIN", entidad = "mountains", detalle = "Datos de montaña actualizados")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public MountainResponse actualizar(Integer id, UpdateMountainRequest request) {
        Mountain mountain = findById(id);
        mountain.setNombre(request.nombre());
        mountain.setRegion(request.region());
        mountain.setAltitud(request.altitud());
        mountain.setPais(request.pais());
        return toResponse(mountainRepository.save(mountain));
    }

    @Auditable(accion = "DELETE_MOUNTAIN", entidad = "mountains", detalle = "Montaña eliminada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public void eliminar(Integer id) {
        Mountain mountain = findById(id);
        long rutasAsociadas = rutaRepository.countByMountainId(id);
        if (rutasAsociadas > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "No se puede eliminar la montaña porque tiene " + rutasAsociadas +
                    " ruta(s) asociada(s). Elimina primero las rutas.");
        }
        mountainRepository.delete(mountain);
        log.info("Montaña eliminada: id={}", id);
    }

    // =========================================================================
    // Acceso por nivel
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<AccesoNivelResponse> obtenerAccesoPorNivel() {
        return accesoRepository.findAllByOrderByNivelSocioNivelAsc().stream()
                .map(this::toAccesoResponse)
                .toList();
    }

    @Auditable(accion = "UPDATE_ACCESO_NIVEL", entidad = "acceso_ruta_por_nivel", detalle = "Umbrales de dificultad por nivel de socio actualizados")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public AccesoNivelResponse actualizarAccesoPorNivel(
            String nivelSocioId, UpdateAccesoNivelRequest request, UUID updatedById) {

        AccesoRutaPorNivel acceso = accesoRepository.findByNivelSocioId(nivelSocioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Nivel de socio no encontrado: " + nivelSocioId));

        acceso.setMaxIfas(findEscala(request.maxIfasId()));
        acceso.setMaxRoca(findRoca(request.maxRocaId()));
        acceso.setMaxHielo(findHielo(request.maxHieloId()));
        acceso.setMaxCompromiso(findCompromiso(request.maxCompromisoId()));
        acceso.setMaxYosemite(findYosemite(request.maxYosemiteId()));
        acceso.setMaxSaddayTecnico(findSadday(request.maxSaddayTecnicoId()));
        acceso.setMaxSaddayFisico(findSadday(request.maxSaddayFisicoId()));
        acceso.setUpdatedAt(LocalDateTime.now());
        acceso.setUpdatedBy(findSocio(updatedById));

        log.info("Acceso por nivel actualizado: nivel={}, updatedBy={}", nivelSocioId, updatedById);
        return toAccesoResponse(accesoRepository.save(acceso));
    }

    // =========================================================================
    // Helpers — finders
    // =========================================================================

    Mountain findById(Integer id) {
        return mountainRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOUNTAIN_NOT_FOUND));
    }

    EscalaAlpinaIfas findEscala(String id) {
        return escalaRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Escala IFAS no encontrada: " + id));
    }

    DificultadRoca findRoca(String id) {
        return rocaRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dificultad roca no encontrada: " + id));
    }

    DificultadHielo findHielo(String id) {
        return hieloRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dificultad hielo no encontrada: " + id));
    }

    Compromiso findCompromiso(String id) {
        return compromisoRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Compromiso no encontrado: " + id));
    }

    SistemaClasesYosemite findYosemite(String id) {
        return yosemiteRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Yosemite clase no encontrada: " + id));
    }

    SaddayRiesgoExigencia findSadday(String id) {
        return saddayRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Sadday riesgo no encontrado: " + id));
    }

    private Socio findSocio(UUID id) {
        return socioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
    }

    // =========================================================================
    // Helpers — Specification
    // =========================================================================

    private Specification<Mountain> buildSpec(String q, String region) {
        Specification<Mountain> spec = (root, query, cb) -> cb.conjunction();

        if (q != null && !q.isBlank()) {
            String escaped = q.replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombre")), pattern),
                    cb.like(cb.lower(root.get("region")), pattern)
            ));
        }
        if (region != null && !region.isBlank()) {
            String escapedRegion = region.replace("%", "\\%").replace("_", "\\_");
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("region")), "%" + escapedRegion.toLowerCase() + "%"));
        }
        return spec;
    }

    // =========================================================================
    // Helpers — mapping
    // =========================================================================

    private MountainResponse toResponse(Mountain m) {
        return new MountainResponse(m.getId(), m.getNombre(), m.getRegion(), m.getAltitud(),
                m.getPais(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private MountainSummaryResponse toSummaryResponse(Mountain m) {
        return new MountainSummaryResponse(m.getId(), m.getNombre(), m.getRegion(), m.getAltitud(), m.getPais());
    }

    private AccesoNivelResponse toAccesoResponse(AccesoRutaPorNivel a) {
        return new AccesoNivelResponse(
                a.getId(),
                a.getNivelSocio().getId(),
                a.getNivelSocio().getNombre(),
                a.getMaxIfas().getId(),
                a.getMaxIfas().getGrado(),
                a.getMaxRoca().getId(),
                a.getMaxRoca().getUiaa(),
                a.getMaxHielo().getId(),
                a.getMaxHielo().getGrado(),
                a.getMaxCompromiso().getId(),
                a.getMaxCompromiso().getTipo(),
                a.getMaxYosemite().getId(),
                a.getMaxYosemite().getTipo(),
                a.getMaxSaddayTecnico().getId(),
                a.getMaxSaddayTecnico().getEscala(),
                a.getMaxSaddayFisico().getId(),
                a.getMaxSaddayFisico().getEscala(),
                a.getUpdatedAt()
        );
    }
}
