package com.sadday.app.actas.service;

import com.sadday.app.actas.dto.*;
import com.sadday.app.actas.entity.ActaInformeSalida;
import com.sadday.app.actas.entity.ActaReunion;
import com.sadday.app.actas.entity.AsistenteReunion;
import com.sadday.app.actas.entity.TipoActa;
import com.sadday.app.actas.repository.ActaInformeSalidaRepository;
import com.sadday.app.actas.repository.ActaReunionRepository;
import com.sadday.app.actas.repository.AsistenteReunionRepository;
import com.sadday.app.informes.entity.InformeSalida;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.security.audit.AuditAspect.Auditable;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Servicio del módulo Actas de Reunión.
 *
 * <p>Reglas de acceso:
 * <ul>
 *   <li>Crear / Actualizar / Eliminar: solo Admin o Secretaria.</li>
 *   <li>Gestionar asistentes e informes vinculados: Admin o Secretaria.</li>
 *   <li>Consultar (listar, detalle): cualquier usuario autenticado.</li>
 * </ul>
 *
 * <p>La búsqueda por texto usa el índice GIN + {@code search_vector} de PostgreSQL
 * mediante {@code plainto_tsquery('spanish', q)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ActaService {

    private final ActaReunionRepository       actaRepository;
    private final AsistenteReunionRepository  asistenteRepository;
    private final ActaInformeSalidaRepository informeLinkRepository;
    private final SocioRepository             socioRepository;
    private final InformeSalidaRepository     informeSalidaRepository;
    private final ActaMdParser                actaMdParser;

    // =========================================================================
    // Listar
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<ActaSummaryResponse> listar(String q, TipoActa tipo, Pageable pageable) {
        String qParam = (q != null && q.isBlank()) ? null : q;
        String tipoFiltro;
        if (esSocioRegular()) {
            if (tipo == TipoActa.DIRECTIVA) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            tipoFiltro = TipoActa.SOCIOS.name();
        } else {
            tipoFiltro = tipo != null ? tipo.name() : null;
        }
        return actaRepository.buscarConFts(tipoFiltro, qParam, pageable)
                .map(this::toSummary);
    }

    // =========================================================================
    // Obtener detalle
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ActaResponse obtener(UUID id) {
        ActaReunion acta = findActa(id);
        // Socios regulares solo pueden ver actas de tipo SOCIOS
        if (esSocioRegular() && acta.getTipoActa() == TipoActa.DIRECTIVA) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return toResponse(acta,
                asistenteRepository.findByActaId(id),
                informeLinkRepository.findByActaId(id));
    }

    // =========================================================================
    // Crear
    // =========================================================================

    @Auditable(accion = "CREAR_ACTA", entidad = "actas_reunion", detalle = "Acta de reunión creada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ActaResponse crear(CreateActaRequest request, UUID currentUserId) {
        Socio creadaPor = socioRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        Socio presidente = resolverSocioOpcional(request.presidenteReunionId());
        Socio secretaria = resolverSocioOpcional(request.secretariaReunionId());

        ActaReunion acta = ActaReunion.builder()
                .tipoActa(request.tipoActa())
                .numeroReunion(request.numeroReunion())
                .fecha(request.fecha())
                .hora(request.hora())
                .horaFin(request.horaFin())
                .lugar(request.lugar())
                .actividadesRealizadasDesc(request.actividadesRealizadasDesc())
                .actividadesPorRealizar(request.actividadesPorRealizar())
                .acuerdos(request.acuerdos())
                .varios(request.varios())
                .observaciones(request.observaciones())
                .creadaPor(creadaPor)
                .presidenteReunion(presidente)
                .secretariaReunion(secretaria)
                .build();

        actaRepository.save(acta);

        // Asistentes iniciales (opcionales)
        if (request.asistentesIds() != null) {
            request.asistentesIds().forEach(socioId -> agregarAsistenteInterno(acta, socioId));
        }

        // Informes vinculados (opcionales)
        if (request.informesIds() != null) {
            request.informesIds().forEach(informeId -> agregarInformeInterno(acta, informeId));
        }

        log.info("Acta creada: {} — {}", acta.getId(), acta.getFecha());
        return toResponse(acta,
                asistenteRepository.findByActaId(acta.getId()),
                informeLinkRepository.findByActaId(acta.getId()));
    }

    // =========================================================================
    // Actualizar
    // =========================================================================

    @Auditable(accion = "ACTUALIZAR_ACTA", entidad = "actas_reunion", detalle = "Acta de reunión actualizada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ActaResponse actualizar(UUID id, UpdateActaRequest request) {
        ActaReunion acta = findActa(id);

        if (request.tipoActa() != null)                  acta.setTipoActa(request.tipoActa());
        if (request.numeroReunion() != null)             acta.setNumeroReunion(request.numeroReunion());
        if (request.fecha() != null)                     acta.setFecha(request.fecha());
        if (request.hora() != null)                      acta.setHora(request.hora());
        if (request.horaFin() != null)                   acta.setHoraFin(request.horaFin());
        if (request.lugar() != null)                     acta.setLugar(request.lugar());
        if (request.actividadesRealizadasDesc() != null) acta.setActividadesRealizadasDesc(request.actividadesRealizadasDesc());
        if (request.actividadesPorRealizar() != null)    acta.setActividadesPorRealizar(request.actividadesPorRealizar());
        if (request.acuerdos() != null)                  acta.setAcuerdos(request.acuerdos());
        if (request.varios() != null)                    acta.setVarios(request.varios());
        if (request.observaciones() != null)             acta.setObservaciones(request.observaciones());
        if (request.presidenteReunionId() != null)       acta.setPresidenteReunion(resolverSocioOpcional(request.presidenteReunionId()));
        if (request.secretariaReunionId() != null)       acta.setSecretariaReunion(resolverSocioOpcional(request.secretariaReunionId()));

        return toResponse(acta,
                asistenteRepository.findByActaId(id),
                informeLinkRepository.findByActaId(id));
    }

    // =========================================================================
    // Eliminar
    // =========================================================================

    @Auditable(accion = "ELIMINAR_ACTA", entidad = "actas_reunion", detalle = "Acta de reunión eliminada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public void eliminar(UUID id) {
        ActaReunion acta = findActa(id);
        actaRepository.delete(acta);
        log.info("Acta eliminada: {}", id);
    }

    // =========================================================================
    // Asistentes
    // =========================================================================

    @Auditable(accion = "AGREGAR_ASISTENTE_ACTA", entidad = "asistentes_reunion", detalle = "Asistente agregado al acta de reunión")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public AsistenteResponse agregarAsistente(UUID actaId, AgregarAsistenteRequest request) {
        ActaReunion acta = findActa(actaId);

        if (asistenteRepository.existsByActaIdAndSocioId(actaId, request.socioId())) {
            throw new BusinessException(ErrorCode.ALREADY_INSCRIBED,
                    "El socio ya está registrado como asistente de esta acta");
        }

        AsistenteReunion asistente = agregarAsistenteInterno(acta, request.socioId());
        return toAsistenteResponse(asistente);
    }

    @Auditable(accion = "ELIMINAR_ASISTENTE_ACTA", entidad = "asistentes_reunion", detalle = "Asistente eliminado del acta de reunión")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public void eliminarAsistente(UUID actaId, Long asistenteId) {
        findActa(actaId);
        AsistenteReunion asistente = asistenteRepository.findById(asistenteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Asistente no encontrado"));

        if (!asistente.getActa().getId().equals(actaId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Asistente no encontrado");
        }

        asistenteRepository.delete(asistente);
    }

    // =========================================================================
    // Informes vinculados
    // =========================================================================

    @Auditable(accion = "VINCULAR_INFORME_ACTA", entidad = "acta_informes_salida", detalle = "Informe de salida vinculado al acta")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public InformeLinkResponse agregarInforme(UUID actaId, AgregarInformeActaRequest request) {
        ActaReunion acta = findActa(actaId);

        if (informeLinkRepository.existsByActaIdAndInformeId(actaId, request.informeId())) {
            throw new BusinessException(ErrorCode.ALREADY_INSCRIBED,
                    "El informe ya está vinculado a esta acta");
        }

        ActaInformeSalida link = agregarInformeInterno(acta, request.informeId());
        return toInformeLinkResponse(link);
    }

    @Auditable(accion = "DESVINCULAR_INFORME_ACTA", entidad = "acta_informes_salida", detalle = "Informe de salida desvinculado del acta")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public void eliminarInforme(UUID actaId, Long linkId) {
        findActa(actaId);
        ActaInformeSalida link = informeLinkRepository.findById(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Vínculo de informe no encontrado"));

        if (!link.getActa().getId().equals(actaId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Vínculo de informe no encontrado");
        }

        informeLinkRepository.delete(link);
    }

    // =========================================================================
    // Importación desde .md
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SECRETARIA')")
    public ActaImportPreviewResponse previewImportacion(String contenidoMd) {
        return actaMdParser.parsear(contenidoMd);
    }

    @Auditable(accion = "IMPORTAR_ACTA", entidad = "actas_reunion", detalle = "Acta importada desde archivo Markdown")
    @PreAuthorize("hasRole('SECRETARIA')")
    public ActaResponse confirmarImportacion(ActaImportConfirmRequest request, UUID currentUserId) {
        Socio creadaPor   = socioRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
        Socio presidente  = resolverSocioOpcional(request.presidenteReunionId());
        Socio secretaria  = resolverSocioOpcional(request.secretariaReunionId());

        ActaReunion acta = ActaReunion.builder()
                .tipoActa(request.tipoActa())
                .numeroReunion(request.numeroReunion())
                .fecha(request.fecha())
                .hora(request.hora())
                .horaFin(request.horaFin())
                .lugar(request.lugar())
                .actividadesRealizadasDesc(request.actividadesRealizadasDesc())
                .actividadesPorRealizar(request.actividadesPorRealizar())
                .acuerdos(request.acuerdos())
                .varios(request.varios())
                .observaciones(request.observaciones())
                .creadaPor(creadaPor)
                .presidenteReunion(presidente)
                .secretariaReunion(secretaria)
                .build();

        actaRepository.save(acta);

        // Asistentes: resueltos y no resueltos
        if (request.asistentes() != null) {
            for (ActaImportConfirmRequest.AsistenteConfirmDto dto : request.asistentes()) {
                Socio socio = null;
                if (dto.socioId() != null) {
                    socio = socioRepository.findById(dto.socioId()).orElse(null);
                }
                // Evitar duplicados si ya existe el socio en esta acta
                if (socio != null && asistenteRepository.existsByActaIdAndSocioId(acta.getId(), socio.getId())) {
                    continue;
                }
                AsistenteReunion asistente = AsistenteReunion.builder()
                        .acta(acta)
                        .socio(socio)
                        .nombreRaw(dto.nombreRaw())
                        .build();
                asistenteRepository.save(asistente);
            }
        }

        log.info("Acta importada desde .md: {} — {}", acta.getId(), acta.getFecha());
        return toResponse(acta,
                asistenteRepository.findByActaId(acta.getId()),
                informeLinkRepository.findByActaId(acta.getId()));
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private ActaReunion findActa(UUID id) {
        return actaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTA_NOT_FOUND));
    }

    private AsistenteReunion agregarAsistenteInterno(ActaReunion acta, UUID socioId) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
        AsistenteReunion asistente = AsistenteReunion.builder()
                .acta(acta).socio(socio)
                .nombreRaw(socio.getNombre() + " " + socio.getApellido())
                .build();
        return asistenteRepository.save(asistente);
    }

    private Socio resolverSocioOpcional(UUID socioId) {
        if (socioId == null) return null;
        return socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
    }

    private ActaInformeSalida agregarInformeInterno(ActaReunion acta, UUID informeId) {
        InformeSalida informe = informeSalidaRepository.findById(informeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));
        ActaInformeSalida link = ActaInformeSalida.builder().acta(acta).informe(informe).build();
        return informeLinkRepository.save(link);
    }

    private ActaResponse toResponse(ActaReunion acta,
                                    List<AsistenteReunion> asistentes,
                                    List<ActaInformeSalida> informes) {
        Socio presidente = acta.getPresidenteReunion();
        Socio secretaria = acta.getSecretariaReunion();
        return new ActaResponse(
                acta.getId(),
                acta.getTipoActa(),
                acta.getNumeroReunion(),
                acta.getFecha(),
                acta.getHora(),
                acta.getHoraFin(),
                acta.getLugar(),
                acta.getActividadesRealizadasDesc(),
                acta.getActividadesPorRealizar(),
                acta.getAcuerdos(),
                acta.getVarios(),
                acta.getObservaciones(),
                presidente != null ? presidente.getId()   : null,
                presidente != null ? presidente.getNombre() + " " + presidente.getApellido() : null,
                secretaria != null ? secretaria.getId()   : null,
                secretaria != null ? secretaria.getNombre() + " " + secretaria.getApellido() : null,
                asistentes.stream().map(this::toAsistenteResponse).toList(),
                informes.stream().map(this::toInformeLinkResponse).toList(),
                acta.getDocumento() != null ? acta.getDocumento().getId() : null,
                acta.getDocumento() != null ? acta.getDocumento().getFilename() : null,
                acta.getCreadaPor().getId(),
                acta.getCreadaPor().getNombre() + " " + acta.getCreadaPor().getApellido(),
                acta.getCreatedAt(),
                acta.getUpdatedAt()
        );
    }

    private ActaSummaryResponse toSummary(ActaReunion acta) {
        Socio presidente = acta.getPresidenteReunion();
        int totalAsistentes = asistenteRepository.countByActaId(acta.getId());
        return new ActaSummaryResponse(
                acta.getId(),
                acta.getTipoActa(),
                acta.getNumeroReunion(),
                acta.getFecha(),
                acta.getHora(),
                acta.getHoraFin(),
                acta.getLugar(),
                totalAsistentes,
                presidente != null ? presidente.getId() : null,
                presidente != null ? presidente.getNombre() + " " + presidente.getApellido() : null,
                acta.getCreadaPor().getId(),
                acta.getCreadaPor().getNombre() + " " + acta.getCreadaPor().getApellido(),
                acta.getCreatedAt()
        );
    }

    private AsistenteResponse toAsistenteResponse(AsistenteReunion a) {
        Socio socio = a.getSocio();
        return new AsistenteResponse(
                a.getId(),
                socio != null ? socio.getId()       : null,
                socio != null ? socio.getNombre()   : null,
                socio != null ? socio.getApellido() : null,
                a.getNombreRaw()
        );
    }

    private InformeLinkResponse toInformeLinkResponse(ActaInformeSalida link) {
        InformeSalida informe = link.getInforme();
        return new InformeLinkResponse(
                link.getId(),
                informe.getId(),
                informe.getSalida().getId(),
                informe.getSalida().getNombre()
        );
    }

    /**
     * Retorna {@code true} si el usuario autenticado tiene solo el rol SOCIO
     * (no es Admin, Secretaria ni Directivo).
     */
    private boolean esSocioRegular() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(a -> a.equals("ROLE_ADMIN")
                        || a.equals("ROLE_SECRETARIA")
                        || a.equals("ROLE_DIRECTIVO"));
    }
}
