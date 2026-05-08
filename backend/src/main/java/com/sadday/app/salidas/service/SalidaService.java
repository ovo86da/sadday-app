package com.sadday.app.salidas.service;

import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.mountains.dto.RutaDocumentoResponse;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.entity.TipoActividad;
import com.sadday.app.mountains.repository.RutaRepository;
import com.sadday.app.mountains.service.RutaDocumentoService;
import com.sadday.app.salidas.dto.*;
import com.sadday.app.salidas.entity.*;
import com.sadday.app.salidas.repository.*;
import com.sadday.app.security.audit.AuditAspect.Auditable;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.entity.ConfiguracionSistema;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.repository.ConfiguracionSistemaRepository;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.ClasificacionSocioRepository;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio del módulo Salidas.
 *
 * <p>Gestiona el ciclo de vida de una salida (planificación, inscripciones, participantes,
 * dignidades) aplicando las reglas de negocio del club:
 * <ul>
 *   <li>Solo Directivo/Admin/Secretaria pueden crear y modificar salidas.</li>
 *   <li>Cualquier socio autenticado puede inscribirse (con validaciones de nivel y habilitación).</li>
 *   <li>Si el nivel del socio es insuficiente, la inscripción queda en PENDIENTE_APROBACION.</li>
 *   <li>Un Directivo Y el Jefe de Salida deben aprobar para que pase a INSCRITO.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SalidaService {

    private static final String CONFIG_BLOQUEAR_INHABILITADOS = "BLOQUEAR_INSCRIPCION_INHABILITADOS";

    /** Estados que cuentan como participación activa (para capacidad e historial). */
    private static final List<EstadoInscripcion> ESTADOS_ACTIVOS =
            List.of(EstadoInscripcion.INSCRITO, EstadoInscripcion.CONFIRMADO,
                    EstadoInscripcion.PENDIENTE_APROBACION);

    private final SalidaRepository                    salidaRepository;
    private final SalidaParticipanteRepository         participanteRepository;
    private final SalidaParticipanteDignidadRepository dignidadRepository;
    private final PublicoObjetivoRepository            publicoObjetivoRepo;
    private final FormatoSalidaRepository              formatoSalidaRepo;
    private final DignidadRepository                   dignidadRepo;
    private final RutaRepository                       rutaRepository;
    private final SocioRepository                      socioRepository;
    private final ClasificacionSocioRepository         clasifSocioRepo;
    private final ConfiguracionSistemaRepository       configRepo;
    private final InformeSalidaRepository              informeRepository;
    private final AuditService                         auditService;
    private final RutaDocumentoService                 rutaDocumentoService;

    // =========================================================================
    // Lookups
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SalidaLookupsResponse obtenerLookups() {
        var publicosObjetivo = publicoObjetivoRepo.findAllByOrderByOrdenAsc().stream()
                .map(p -> new SalidaLookupsResponse.PublicoObjetivoItem(p.getId(), p.getNombre()))
                .toList();

        var formatosSalida = formatoSalidaRepo.findAllByOrderByOrdenAsc().stream()
                .map(f -> new SalidaLookupsResponse.FormatoSalidaItem(f.getId(), f.getNombre()))
                .toList();

        var dignidades = dignidadRepo.findAll().stream()
                .map(d -> new SalidaLookupsResponse.DignidadItem(d.getId(), d.getNombre(), d.getDescripcion()))
                .toList();

        var estadosSalida = Arrays.stream(EstadoSalida.values()).map(Enum::name).toList();
        var estadosInscripcion = Arrays.stream(EstadoInscripcion.values()).map(Enum::name).toList();

        return new SalidaLookupsResponse(publicosObjetivo, formatosSalida, dignidades, estadosSalida, estadosInscripcion);
    }

    // =========================================================================
    // CRUD Salidas
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Page<SalidaSummaryResponse> listar(
            EstadoSalida estado, LocalDate fechaInicio, String q, Long rutaId, Pageable pageable) {

        Page<Salida> page = salidaRepository.findAll(buildSpec(estado, fechaInicio, q, rutaId), pageable);

        List<UUID> ids = page.getContent().stream().map(Salida::getId).toList();
        Set<UUID> conInforme = ids.isEmpty() ? Set.of() : informeRepository.findSalidaIdsWithInforme(ids);

        return page.map(s -> toSummaryResponse(s, contarInscritos(s.getId()), conInforme.contains(s.getId())));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SalidaResponse obtener(UUID id) {
        return buildSalidaResponse(findById(id));
    }

    /**
     * Devuelve las salidas activas (PLANIFICADA o EN_CURSO) cuyos rangos de fechas se
     * solapan con el intervalo dado. Se excluye la propia salida al editar ({@code excludeId}).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public List<SolapamientoResponse> verificarSolapamiento(
            LocalDate fechaInicio, LocalDate fechaFin, UUID excludeId) {

        List<EstadoSalida> activos = List.of(EstadoSalida.PLANIFICADA, EstadoSalida.EN_CURSO);
        return salidaRepository.findSolapadas(activos, fechaInicio, fechaFin, excludeId)
                .stream()
                .map(s -> new SolapamientoResponse(s.getId(), s.getNombre(), s.getFechaInicio(), s.getFechaFin()))
                .toList();
    }

    @Auditable(accion = "CREATE_SALIDA", entidad = "salida", detalle = "Salida creada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public SalidaResponse crear(CreateSalidaRequest request, UUID creadoPorId) {
        Ruta ruta = findRuta(request.rutaId());
        ClasificacionSocio nivel = resolverNivelMinimo(request.nivelMinimoRequeridoId());
        Socio creadoPor = findSocio(creadoPorId);

        Salida salida = Salida.builder()
                .nombre(request.nombre())
                .fechaInicio(request.fechaInicio())
                .horaEncuentroClub(request.horaEncuentroClub())
                .fechaFin(request.fechaFin())
                .horaEstimadaRegresoClub(request.horaEstimadaRegresoClub())
                .ruta(ruta)
                .tipoActividad(parseTipoActividad(request.tipoActividad()))
                .publicoObjetivo(findPublicoObjetivo(request.publicoObjetivoId()))
                .formatoSalida(findFormatoSalida(request.formatoSalidaId()))
                .nivelMinimoRequerido(nivel)
                .capacidadMaxima(request.capacidadMaxima())
                .creadoPor(creadoPor)
                .build();

        Salida saved = salidaRepository.save(salida);
        log.info("Salida creada: id={}, nombre={}", saved.getId(), saved.getNombre());
        return buildSalidaResponse(saved);
    }

    @Auditable(accion = "UPDATE_SALIDA", entidad = "salida", detalle = "Datos de la salida actualizados")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public SalidaResponse actualizar(UUID id, UpdateSalidaRequest request) {
        Salida salida = findById(id);
        validarEstadoModificable(salida);

        salida.setNombre(request.nombre());
        salida.setFechaInicio(request.fechaInicio());
        salida.setHoraEncuentroClub(request.horaEncuentroClub());
        salida.setFechaFin(request.fechaFin());
        salida.setHoraEstimadaRegresoClub(request.horaEstimadaRegresoClub());
        salida.setRuta(findRuta(request.rutaId()));
        salida.setTipoActividad(parseTipoActividad(request.tipoActividad()));
        salida.setPublicoObjetivo(findPublicoObjetivo(request.publicoObjetivoId()));
        salida.setFormatoSalida(findFormatoSalida(request.formatoSalidaId()));
        salida.setNivelMinimoRequerido(resolverNivelMinimo(request.nivelMinimoRequeridoId()));
        salida.setCapacidadMaxima(request.capacidadMaxima());

        return buildSalidaResponse(salidaRepository.save(salida));
    }

    @Auditable(accion = "CAMBIAR_ESTADO_SALIDA", entidad = "salida", detalle = "Estado de la salida actualizado")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public void cambiarEstado(UUID id, CambiarEstadoSalidaRequest request) {
        Salida salida = findById(id);
        salida.setEstado(request.estado());
        log.info("Estado de salida cambiado: id={}, estado={}", id, request.estado());
    }

    @Auditable(accion = "DELETE_SALIDA", entidad = "salida", detalle = "Salida eliminada (soft)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public void eliminar(UUID id, EliminarSalidaRequest request, UUID actorId) {
        Salida salida = findById(id);
        if (salida.getEstado() == EstadoSalida.REALIZADA) {
            throw new BusinessException(ErrorCode.SALIDA_NOT_PLANIFICADA,
                    "No se puede eliminar una salida que ya fue realizada");
        }
        salida.setEliminada(true);
        salida.setEliminadaEn(LocalDateTime.now());
        salida.setEliminadaPor(findSocio(actorId));
        salida.setMotivoEliminacion(request.motivo());
        salidaRepository.save(salida);
        log.info("Salida eliminada (soft): id={}, actor={}", id, actorId);
    }

    @Auditable(accion = "CANCELAR_SALIDA", entidad = "salida", detalle = "Salida cancelada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public SalidaResponse cancelar(UUID id, CancelarSalidaRequest request, UUID actorId) {
        Salida salida = findById(id);
        if (salida.getEstado() == EstadoSalida.CANCELADA) {
            throw new BusinessException(ErrorCode.SALIDA_CANCELADA, "La salida ya está cancelada");
        }
        if (salida.getEstado() == EstadoSalida.REALIZADA) {
            throw new BusinessException(ErrorCode.SALIDA_NOT_PLANIFICADA,
                    "No se puede cancelar una salida que ya fue realizada");
        }
        salida.setEstado(EstadoSalida.CANCELADA);
        salida.setMotivoCancelacion(request.motivo());
        salida.setCanceladaPor(findSocio(actorId));
        salida.setCanceladaEn(LocalDateTime.now());
        salida.setInscripcionesCerradas(true);
        Salida saved = salidaRepository.save(salida);
        log.info("Salida cancelada: id={}, actor={}", id, actorId);
        return buildSalidaResponse(saved);
    }

    // =========================================================================
    // Inscripciones
    // =========================================================================

    @Auditable(accion = "INSCRIBIR_SOCIO", entidad = "salida_participantes", idArgName = "salidaId", detalle = "Socio inscrito en la salida")
    @PreAuthorize("isAuthenticated()")
    public ParticipanteResponse inscribir(UUID salidaId, InscribirRequest request, UUID currentUserId) {
        boolean esJefe = esJefeDeSalida(salidaId);

        if (!currentUserId.equals(request.socioId()) && !tieneRolPrivilegiado() && !esJefe) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Solo puedes inscribirte a ti mismo. Pide a un Directivo o Secretaria que te inscriba.");
        }

        Salida salida = salidaRepository.findByIdWithLock(salidaId)
                .filter(s -> !s.isEliminada())
                .orElseThrow(() -> new BusinessException(ErrorCode.SALIDA_NOT_FOUND));
        Socio socio = findSocio(request.socioId());

        if (salida.getEstado() == EstadoSalida.CANCELADA) {
            throw new BusinessException(ErrorCode.SALIDA_CANCELADA);
        }
        boolean estadoPermitido = salida.getEstado() == EstadoSalida.PLANIFICADA
                || ((esJefe || tieneRolPrivilegiado()) &&
                    (salida.getEstado() == EstadoSalida.EN_CURSO || salida.getEstado() == EstadoSalida.REALIZADA));
        if (!estadoPermitido) {
            throw new BusinessException(ErrorCode.SALIDA_NOT_PLANIFICADA,
                    "Solo se puede inscribir en salidas PLANIFICADAS");
        }
        if (salida.isInscripcionesCerradas() && !tieneRolPrivilegiado()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Las inscripciones para esta salida están cerradas");
        }
        if (participanteRepository.existsBySalidaIdAndSocioId(salidaId, socio.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_INSCRIBED);
        }
        if (salida.getCapacidadMaxima() != null && contarInscritos(salidaId) >= salida.getCapacidadMaxima()) {
            throw new BusinessException(ErrorCode.SALIDA_FULL);
        }

        // Verificar estado de acceso: solo socios ACTIVE pueden inscribirse
        String codigoAcceso = socio.getEstadoAcceso().getCodigo();
        if (!"ACTIVE".equals(codigoAcceso)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "El socio no puede inscribirse: acceso " + codigoAcceso);
        }
        // Verificar habilitación para salidas (independiente del acceso al sistema)
        if ("Inhabilitado".equals(socio.getEstadoHabilitacion().getNombre()) && isBloqueoInhabilitadosActivo()) {
            throw new BusinessException(ErrorCode.SOCIO_INHABILITADO,
                    "Socio inhabilitado, por favor igualese en las cuotas.");
        }

        // Verificación de nivel: insuficiente → PENDIENTE_APROBACION, suficiente → INSCRITO
        ClasificacionSocio nivelMinimo = salida.getNivelMinimoRequerido();
        ClasificacionSocio nivelSocio  = socio.getNivelTecnico();
        EstadoInscripcion estadoInicial = EstadoInscripcion.INSCRITO;

        if (nivelMinimo != null) {
            if (nivelSocio == null || nivelSocio.getNivel() < nivelMinimo.getNivel()) {
                estadoInicial = EstadoInscripcion.PENDIENTE_APROBACION;
                log.info("Inscripción con nivel insuficiente — PENDIENTE_APROBACION: " +
                         "socio={}, nivelSocio={}, nivelMinimo={}",
                        socio.getId(),
                        nivelSocio != null ? nivelSocio.getNombre() : "sin nivel",
                        nivelMinimo.getNombre());
            }
        }

        SalidaParticipante participante = SalidaParticipante.builder()
                .salida(salida)
                .socio(socio)
                .estadoInscripcion(estadoInicial)
                .build();

        SalidaParticipante saved = participanteRepository.save(participante);
        log.info("Inscripción creada: salidaId={}, socioId={}, estado={}",
                salidaId, socio.getId(), estadoInicial);
        return toParticipanteResponse(saved, List.of(), nivelMinimo);
    }

    @Auditable(accion = "CANCELAR_INSCRIPCION", entidad = "salida_participantes", detalle = "Inscripción cancelada")
    @PreAuthorize("isAuthenticated()")
    public void cancelarInscripcion(UUID salidaId, Long participanteId, UUID currentUserId) {
        SalidaParticipante participante = findParticipante(participanteId, salidaId);

        if (!participante.getSocio().getId().equals(currentUserId) && !tieneRolPrivilegiado()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Solo puedes cancelar tu propia inscripción");
        }

        Salida salida = participante.getSalida();
        if (salida.isInscripcionesCerradas() && !tieneRolPrivilegiado()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Las inscripciones están cerradas; no se permite cancelar");
        }

        EstadoInscripcion estado = participante.getEstadoInscripcion();

        if (estado == EstadoInscripcion.CANCELADO || estado == EstadoInscripcion.NEGADO
                || estado == EstadoInscripcion.NO_FUE || estado == EstadoInscripcion.CONFIRMADO) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se puede cancelar una inscripción en estado " + estado);
        }

        // INSCRITO: solo se permite cancelar hasta 48 horas antes del inicio de la salida
        if (estado == EstadoInscripcion.INSCRITO) {
            LocalDateTime limite = participante.getSalida().getFechaInicio()
                    .atStartOfDay()
                    .minusHours(48);
            if (LocalDateTime.now().isAfter(limite)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "No se puede cancelar la inscripción dentro de las 48 horas previas a la salida");
            }
        }
        // PENDIENTE_APROBACION: se puede cancelar en cualquier momento (es solo una solicitud)

        boolean esJefeParticipante = dignidadRepository
                .existsByParticipanteIdAndDignidad_Nombre(participanteId, "Jefe de Salida");

        if (esJefeParticipante) {
            if (!tieneRolPrivilegiado()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Usted se inscribió en esta salida y fue elegido como Jefe de Salida, " +
                        "por lo que no puede cancelar su inscripción. Si pese a eso decide cancelar, " +
                        "por favor contacte al Jefe de Montaña para solucionar el inconveniente.");
            }
            // Privilegiado retirándose como jefe: registrar la alerta para el JM
            Socio socio = participante.getSocio();
            salida.setJefeAbandonoNombre(socio.getNombre() + " " + socio.getApellido());
            salidaRepository.save(salida);
            log.info("Jefe de salida retirado: salidaId={}, socio={} {}", salidaId,
                    socio.getNombre(), socio.getApellido());
        }

        participanteRepository.delete(participante);
        log.info("Inscripción cancelada: participanteId={}, estado={}", participanteId, estado);
    }

    @Auditable(accion = "CAMBIAR_ESTADO_INSCRIPCION", entidad = "salida_participantes", detalle = "Estado de inscripción actualizado")
    @PreAuthorize("isAuthenticated()")
    public ParticipanteResponse cambiarEstadoInscripcion(
            UUID salidaId, Long participanteId, CambiarEstadoInscripcionRequest request) {

        if (!tieneRolPrivilegiado() && !esJefeDeSalida(salidaId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Solo el Jefe de Salida o el staff pueden modificar el estado de inscripción");
        }

        Salida salida = findById(salidaId);
        SalidaParticipante participante = findParticipante(participanteId, salidaId);

        EstadoInscripcion estadoActual = participante.getEstadoInscripcion();
        EstadoInscripcion estadoNuevo  = request.estadoInscripcion();
        if (!esTransicionValida(estadoActual, estadoNuevo)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Transición de estado no permitida: " + estadoActual + " → " + estadoNuevo);
        }
        participante.setEstadoInscripcion(estadoNuevo);

        List<SalidaParticipanteDignidad> dignidades = dignidadRepository.findByParticipanteId(participanteId);
        return toParticipanteResponse(
                participanteRepository.save(participante), dignidades, salida.getNivelMinimoRequerido());
    }

    /**
     * Decide sobre el riesgo de inscripción de un socio con nivel insuficiente.
     *
     * <p>Un Directivo (o Admin) y el Jefe de Salida deben decidir independientemente.
     * Cualquiera puede negar (→ NEGADO inmediatamente). Solo cuando ambos aprueban → INSCRITO.
     * El motivo es obligatorio tanto para aprobación como para negación.
     */
    @PreAuthorize("isAuthenticated()")
    public ParticipanteResponse decidirRiesgo(UUID salidaId, Long participanteId,
                                              UUID currentUserId, DecidirRiesgoRequest request) {
        Salida salida = findById(salidaId);
        SalidaParticipante participante = findParticipante(participanteId, salidaId);

        if (participante.getEstadoInscripcion() != EstadoInscripcion.PENDIENTE_APROBACION) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El participante no está en estado PENDIENTE_APROBACION");
        }

        boolean esDirectivoOAdmin = esJefeMontanaOAdmin();
        boolean esJefe = esJefeDeSalida(salidaId);

        if (!esDirectivoOAdmin && !esJefe) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Solo un Jefe de Montaña (Directivo con flag JM), Admin, o el Jefe de Salida pueden decidir sobre el riesgo de inscripción");
        }

        if (!request.aprobar()) {
            // Negación inmediata: cualquiera de los dos puede negar
            participante.setEstadoInscripcion(EstadoInscripcion.NEGADO);
            if (esDirectivoOAdmin) {
                participante.setRiesgoAprobadoPorDirectivo(currentUserId);
                participante.setMotivoDirectivo(request.motivo());
            }
            if (esJefe) {
                participante.setRiesgoAprobadoPorJefe(currentUserId);
                participante.setMotivoJefe(request.motivo());
            }
            log.info("Inscripción NEGADA: salidaId={}, participanteId={}, por={}, motivo={}",
                    salidaId, participanteId, currentUserId, request.motivo());
        } else {
            // Aprobación: registrar quién aprueba y su motivo
            if (esDirectivoOAdmin && participante.getRiesgoAprobadoPorDirectivo() == null) {
                participante.setRiesgoAprobadoPorDirectivo(currentUserId);
                participante.setMotivoDirectivo(request.motivo());
                log.info("Riesgo aprobado por directivo: salidaId={}, participanteId={}, directivo={}",
                        salidaId, participanteId, currentUserId);
            }
            if (esJefe && participante.getRiesgoAprobadoPorJefe() == null) {
                participante.setRiesgoAprobadoPorJefe(currentUserId);
                participante.setMotivoJefe(request.motivo());
                log.info("Riesgo aprobado por jefe: salidaId={}, participanteId={}, jefe={}",
                        salidaId, participanteId, currentUserId);
            }

            // Ambas aprobaciones completas → INSCRITO
            if (participante.getRiesgoAprobadoPorDirectivo() != null
                    && participante.getRiesgoAprobadoPorJefe() != null) {
                participante.setEstadoInscripcion(EstadoInscripcion.INSCRITO);
                participante.setRiesgoAprobadoEn(LocalDateTime.now());
                log.info("Inscripción aprobada completamente → INSCRITO: salidaId={}, participanteId={}",
                        salidaId, participanteId);
            }
        }

        String actorUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        Socio socioParticipante = participante.getSocio();
        String detalle = String.format("%s. Socio: %s %s. Salida: %s (%s). Ruta: %s. Motivo: %s",
                request.aprobar() ? "Aprobado" : "Rechazado",
                socioParticipante.getNombre(), socioParticipante.getApellido(),
                salida.getNombre(), salida.getFechaInicio(),
                salida.getRuta() != null ? salida.getRuta().getNombre() : "sin ruta",
                request.motivo());
        auditService.registrar(actorUsername, "DECIDIR_RIESGO_INSCRIPCION", "salida_participantes",
                participanteId, null, null, null, null, "SUCCESS", detalle);

        List<SalidaParticipanteDignidad> dignidades = dignidadRepository.findByParticipanteId(participanteId);
        return toParticipanteResponse(
                participanteRepository.save(participante), dignidades, salida.getNivelMinimoRequerido());
    }

    /**
     * Lista las inscripciones pendientes de aprobación para el usuario autenticado:
     * - Directivo/Admin: todas las PENDIENTE_APROBACION sin aprobación de directivo.
     * - Jefe de Salida: las PENDIENTE_APROBACION sin aprobación de jefe en salidas donde es jefe.
     */
    @PreAuthorize("isAuthenticated()")
    public List<AprobacionPendienteResponse> obtenerAprobacionesPendientes(UUID currentUserId) {
        boolean esDirectivoOAdmin = esJefeMontanaOAdmin();

        List<SalidaParticipante> pendientes;

        if (esDirectivoOAdmin) {
            pendientes = participanteRepository.findPendientesParaDirectivo(EstadoInscripcion.PENDIENTE_APROBACION);
        } else {
            pendientes = participanteRepository.findPendientesParaJefe(currentUserId, EstadoInscripcion.PENDIENTE_APROBACION);
        }

        return pendientes.stream()
                .map(p -> {
                    ClasificacionSocio nivelSocio = p.getSocio().getNivelTecnico();
                    ClasificacionSocio nivelMinimo = p.getSalida().getNivelMinimoRequerido();
                    return new AprobacionPendienteResponse(
                            p.getId(),
                            p.getSalida().getId(),
                            p.getSalida().getNombre(),
                            p.getSalida().getFechaInicio(),
                            p.getSocio().getId(),
                            p.getSocio().getNombre(),
                            p.getSocio().getApellido(),
                            nivelSocio  != null ? nivelSocio.getNombre()  : null,
                            nivelMinimo != null ? nivelMinimo.getNombre() : null,
                            p.getRiesgoAprobadoPorDirectivo() != null,
                            p.getRiesgoAprobadoPorJefe() != null
                    );
                })
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public List<AlertaSinJefeResponse> obtenerAlertasSinJefe() {
        return salidaRepository.findByJefeAbandonoNombreIsNotNull()
                .stream()
                .map(s -> new AlertaSinJefeResponse(
                        s.getId().toString(),
                        s.getNombre(),
                        s.getFechaInicio(),
                        s.getJefeAbandonoNombre()
                ))
                .toList();
    }

    /**
     * Alterna el estado de inscripciones cerradas de una salida.
     *
     * <p>Solo el Jefe de Salida (o Admin/Directivo como fallback de gestión) puede usar esta acción.
     * Cuando está cerrada: no se admiten nuevas inscripciones ni cancelaciones por parte de socios.
     */
    @Auditable(accion = "TOGGLE_INSCRIPCIONES_CERRADAS", entidad = "salida", detalle = "Estado de inscripciones (abiertas/cerradas) actualizado")
    @PreAuthorize("isAuthenticated()")
    public boolean toggleInscripcionesCerradas(UUID salidaId, UUID currentUserId) {
        Salida salida = findById(salidaId);

        boolean esJefe = esJefeDeSalida(salidaId);
        if (!esJefe && !tieneRolPrivilegiado()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Solo el Jefe de Salida puede cerrar o abrir las inscripciones");
        }

        salida.setInscripcionesCerradas(!salida.isInscripcionesCerradas());
        salidaRepository.save(salida);
        log.info("Inscripciones {} para salida {}: por socio={}",
                salida.isInscripcionesCerradas() ? "CERRADAS" : "ABIERTAS",
                salidaId, currentUserId);
        return salida.isInscripcionesCerradas();
    }

    @Auditable(accion = "DESIGNAR_JEFE_SALIDA", entidad = "salida_participantes", detalle = "Jefe de salida designado")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public ParticipanteResponse designarJefeSalida(UUID salidaId, Long participanteId) {
        Salida salida = findById(salidaId);
        SalidaParticipante participante = findParticipante(participanteId, salidaId);

        Dignidad jefeDignidad = dignidadRepo.findByNombre("Jefe de Salida")
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Dignidad 'Jefe de Salida' no encontrada en el catálogo"));

        dignidadRepository.findByParticipante_Salida_IdAndDignidad_Nombre(salidaId, "Jefe de Salida")
                .stream()
                .filter(spd -> !spd.getParticipante().getId().equals(participanteId))
                .forEach(dignidadRepository::delete);

        if (!dignidadRepository.existsByParticipanteIdAndDignidadId(participanteId, jefeDignidad.getId())) {
            dignidadRepository.save(SalidaParticipanteDignidad.builder()
                    .participante(participante)
                    .dignidad(jefeDignidad)
                    .build());
        }

        // Limpiar alerta de jefe retirado al designar uno nuevo
        if (salida.getJefeAbandonoNombre() != null) {
            salida.setJefeAbandonoNombre(null);
            salidaRepository.save(salida);
        }

        List<SalidaParticipanteDignidad> dignidades = dignidadRepository.findByParticipanteId(participanteId);
        return toParticipanteResponse(participante, dignidades, salida.getNivelMinimoRequerido());
    }

    // =========================================================================
    // Dignidades
    // =========================================================================

    @Auditable(accion = "AGREGAR_DIGNIDAD", entidad = "salida_participante_dignidades", detalle = "Dignidad asignada al participante")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public ParticipanteResponse agregarDignidad(UUID salidaId, Long participanteId, AgregarDignidadRequest request) {
        Salida salida = findById(salidaId);
        SalidaParticipante participante = findParticipante(participanteId, salidaId);
        Dignidad dignidad = findDignidad(request.dignidadId());

        if (dignidadRepository.existsByParticipanteIdAndDignidadId(participanteId, request.dignidadId())) {
            throw new BusinessException(ErrorCode.ALREADY_INSCRIBED,
                    "El participante ya tiene esta dignidad asignada");
        }

        dignidadRepository.save(SalidaParticipanteDignidad.builder()
                .participante(participante)
                .dignidad(dignidad)
                .build());

        List<SalidaParticipanteDignidad> dignidades = dignidadRepository.findByParticipanteId(participanteId);
        return toParticipanteResponse(participante, dignidades, salida.getNivelMinimoRequerido());
    }

    @Auditable(accion = "ELIMINAR_DIGNIDAD", entidad = "salida_participante_dignidades", detalle = "Dignidad eliminada del participante")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public void eliminarDignidad(UUID salidaId, Long participanteId, Long dignidadAsignadaId) {
        findById(salidaId);
        findParticipante(participanteId, salidaId);

        SalidaParticipanteDignidad asignada = dignidadRepository.findById(dignidadAsignadaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Dignidad asignada no encontrada"));
        if (!asignada.getParticipante().getId().equals(participanteId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "La dignidad no pertenece a este participante");
        }
        dignidadRepository.delete(asignada);
    }

    // =========================================================================
    // Helpers — finders
    // =========================================================================

    private Salida findById(UUID id) {
        Salida salida = salidaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALIDA_NOT_FOUND));
        if (salida.isEliminada()) {
            throw new BusinessException(ErrorCode.SALIDA_NOT_FOUND);
        }
        return salida;
    }

    private SalidaParticipante findParticipante(Long participanteId, UUID salidaId) {
        SalidaParticipante p = participanteRepository.findById(participanteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Participante no encontrado: " + participanteId));
        if (!p.getSalida().getId().equals(salidaId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "El participante no pertenece a esta salida");
        }
        return p;
    }

    private TipoActividad parseTipoActividad(String value) {
        if (value == null || value.isBlank()) return null;
        return TipoActividad.valueOf(value.toUpperCase());
    }

    /** Devuelve la ruta si el id no es null; null si no aplica (salidas sin ruta de montaña). */
    private Ruta findRuta(Integer rutaId) {
        if (rutaId == null) return null;
        return rutaRepository.findById(rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_NOT_FOUND));
    }

    private PublicoObjetivo findPublicoObjetivo(String id) {
        if (id == null || id.isBlank()) return null;
        return publicoObjetivoRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Público objetivo no encontrado: " + id));
    }

    private FormatoSalida findFormatoSalida(String id) {
        if (id == null || id.isBlank()) return null;
        return formatoSalidaRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Formato de salida no encontrado: " + id));
    }

    private ClasificacionSocio resolverNivelMinimo(String id) {
        if (id == null || id.isBlank()) return null;
        return clasifSocioRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Nivel mínimo no encontrado: " + id));
    }

    private Socio findSocio(UUID id) {
        return socioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
    }

    private Dignidad findDignidad(Integer id) {
        return dignidadRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Dignidad no encontrada: " + id));
    }

    // =========================================================================
    // Helpers — business rules
    // =========================================================================

    private void validarEstadoModificable(Salida salida) {
        if (salida.getEstado() != EstadoSalida.PLANIFICADA) {
            throw new BusinessException(ErrorCode.SALIDA_NOT_PLANIFICADA,
                    "Solo se pueden modificar salidas en estado PLANIFICADA");
        }
    }

    private int contarInscritos(UUID salidaId) {
        return participanteRepository.countBySalidaIdAndEstadoInscripcionIn(salidaId, ESTADOS_ACTIVOS);
    }

    private boolean esTransicionValida(EstadoInscripcion desde, EstadoInscripcion hacia) {
        return switch (desde) {
            case PENDIENTE_APROBACION -> hacia == EstadoInscripcion.INSCRITO || hacia == EstadoInscripcion.NEGADO || hacia == EstadoInscripcion.CANCELADO;
            case INSCRITO             -> hacia == EstadoInscripcion.CONFIRMADO || hacia == EstadoInscripcion.NO_FUE || hacia == EstadoInscripcion.CANCELADO;
            case CONFIRMADO           -> hacia == EstadoInscripcion.NO_FUE;
            case NEGADO, CANCELADO, NO_FUE -> false;
        };
    }

    private boolean isBloqueoInhabilitadosActivo() {
        return configRepo.findByClave(CONFIG_BLOQUEAR_INHABILITADOS)
                .map(ConfiguracionSistema::getValor)
                .map("true"::equalsIgnoreCase)
                .orElse(true);
    }

    /** Resuelve "Nombre Apellido" de un socio por UUID; retorna null si el UUID es null o no existe. */
    private String resolverNombreAprobador(UUID id) {
        if (id == null) return null;
        return socioRepository.findById(id)
                .map(s -> s.getNombre() + " " + s.getApellido())
                .orElse(null);
    }

    /**
     * Revoca la aprobación de riesgo registrada por el usuario autenticado.
     * Un Directivo/Admin revoca la aprobación del slot directivo;
     * el Jefe de Salida revoca la del slot jefe.
     * Si la inscripción estaba en INSCRITO (ambas aprobaciones completadas),
     * vuelve a PENDIENTE_APROBACION.
     */
    @Auditable(accion = "REVOCAR_APROBACION_RIESGO", entidad = "salida_participantes", detalle = "Aprobación de riesgo de inscripción revocada")
    @PreAuthorize("isAuthenticated()")
    public ParticipanteResponse revocarAprobacion(UUID salidaId, Long participanteId) {
        Salida salida = findById(salidaId);
        SalidaParticipante participante = findParticipante(participanteId, salidaId);

        boolean esDirectivoOAdmin = esJefeMontanaOAdmin();
        boolean esJefe = esJefeDeSalida(salidaId);

        if (!esDirectivoOAdmin && !esJefe) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Solo un Directivo/Admin o el Jefe de Salida pueden revocar una aprobación de riesgo");
        }

        EstadoInscripcion estado = participante.getEstadoInscripcion();
        if (estado != EstadoInscripcion.PENDIENTE_APROBACION && estado != EstadoInscripcion.INSCRITO) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Solo se puede revocar una aprobación de inscripciones en estado PENDIENTE_APROBACION o INSCRITO");
        }

        if (esDirectivoOAdmin) {
            if (participante.getRiesgoAprobadoPorDirectivo() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "No hay aprobación de Directivo que revocar");
            }
            participante.setRiesgoAprobadoPorDirectivo(null);
            participante.setMotivoDirectivo(null);
        } else {
            if (participante.getRiesgoAprobadoPorJefe() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "No hay aprobación de Jefe de Salida que revocar");
            }
            participante.setRiesgoAprobadoPorJefe(null);
            participante.setMotivoJefe(null);
        }

        if (estado == EstadoInscripcion.INSCRITO) {
            participante.setEstadoInscripcion(EstadoInscripcion.PENDIENTE_APROBACION);
            participante.setRiesgoAprobadoEn(null);
        }

        log.info("Aprobación revocada: salidaId={}, participanteId={}, rol={}",
                salidaId, participanteId, esDirectivoOAdmin ? "directivo" : "jefe");

        List<SalidaParticipanteDignidad> dignidades = dignidadRepository.findByParticipanteId(participanteId);
        return toParticipanteResponse(participanteRepository.save(participante), dignidades, salida.getNivelMinimoRequerido());
    }

    private boolean tieneRolPrivilegiado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_SECRETARIA") || a.equals("ROLE_DIRECTIVO"));
    }

    /**
     * True si el usuario puede actuar como "Jefe de Montaña" en aprobaciones de riesgo:
     * - ADMIN siempre (sin restricción de flag)
     * - DIRECTIVO solo si tiene el flag esJefeMontana=true
     */
    private boolean esJefeMontanaOAdmin() {
        UUID socioId = getCurrentSocioId();
        if (socioId == null) return false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        boolean esAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (esAdmin) return true;
        boolean esDirectivo = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_DIRECTIVO"::equals);
        if (!esDirectivo) return false;
        return socioRepository.findById(socioId)
                .map(Socio::isEsJefeMontana)
                .orElse(false);
    }

    private UUID getCurrentSocioId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof SaddayAuthDetails details)) return null;
        return details.socioId();
    }

    private boolean esJefeDeSalida(UUID salidaId) {
        UUID socioId = getCurrentSocioId();
        if (socioId == null) return false;
        return dignidadRepository.countBySalidaSocioYDignidad(salidaId, socioId, "Jefe de Salida") > 0;
    }

    // =========================================================================
    // Helpers — Specification
    // =========================================================================

    private Specification<Salida> buildSpec(
            EstadoSalida estado, LocalDate fechaInicio, String q, Long rutaId) {

        Specification<Salida> spec = (root, query, cb) -> cb.isFalse(root.get("eliminada"));

        if (estado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (fechaInicio != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("fechaInicio"), fechaInicio));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.replace("%", "\\%").replace("_", "\\_").toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("nombre")), pattern));
        }
        if (rutaId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("ruta").get("id"), rutaId));
        }
        return spec;
    }

    // =========================================================================
    // Helpers — mapping
    // =========================================================================

    private SalidaResponse buildSalidaResponse(Salida salida) {
        List<SalidaParticipante> participantes = participanteRepository.findBySalidaId(salida.getId());

        List<Long> pIds = participantes.stream().map(SalidaParticipante::getId).toList();
        Map<Long, List<SalidaParticipanteDignidad>> dignidadesByParticipante = pIds.isEmpty()
                ? Map.of()
                : dignidadRepository.findByParticipanteIdIn(pIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getParticipante().getId()));

        long activos = participantes.stream()
                .filter(p -> ESTADOS_ACTIVOS.contains(p.getEstadoInscripcion()))
                .count();

        ClasificacionSocio nivelMinimo = salida.getNivelMinimoRequerido();

        List<ParticipanteResponse> participanteResponses = participantes.stream()
                .map(p -> toParticipanteResponse(p,
                        dignidadesByParticipante.getOrDefault(p.getId(), List.of()),
                        nivelMinimo))
                .toList();

        List<RutaDocumentoResponse> documentosPermiso = (salida.getRuta() != null
                && Boolean.TRUE.equals(salida.getRuta().getRequierePermisos()))
                ? rutaDocumentoService.listarPorRuta(salida.getRuta().getId())
                : List.of();

        return new SalidaResponse(
                salida.getId(),
                salida.getNombre(),
                salida.getFechaInicio(),
                salida.getHoraEncuentroClub(),
                salida.getFechaFin(),
                salida.getHoraEstimadaRegresoClub(),
                salida.getRuta() != null ? salida.getRuta().getId() : null,
                salida.getRuta() != null ? salida.getRuta().getNombre() : null,
                salida.getTipoActividad() != null ? salida.getTipoActividad().name() : null,
                salida.getPublicoObjetivo() != null ? salida.getPublicoObjetivo().getId() : null,
                salida.getPublicoObjetivo() != null ? salida.getPublicoObjetivo().getNombre() : null,
                salida.getFormatoSalida() != null ? salida.getFormatoSalida().getId() : null,
                salida.getFormatoSalida() != null ? salida.getFormatoSalida().getNombre() : null,
                nivelMinimo != null ? nivelMinimo.getId() : null,
                nivelMinimo != null ? nivelMinimo.getNombre() : null,
                salida.getCapacidadMaxima(),
                (int) activos,
                salida.isInscripcionesCerradas(),
                salida.getEstado(),
                salida.getMotivoCancelacion(),
                salida.getCreadoPor().getId(),
                salida.getCreadoPor().getNombre() + " " + salida.getCreadoPor().getApellido(),
                participanteResponses,
                documentosPermiso,
                salida.getCreatedAt(),
                salida.getUpdatedAt()
        );
    }

    private SalidaSummaryResponse toSummaryResponse(Salida s, int totalInscritos, boolean tieneInforme) {
        return new SalidaSummaryResponse(
                s.getId(),
                s.getNombre(),
                s.getFechaInicio(),
                s.getHoraEncuentroClub(),
                s.getFechaFin(),
                s.getRuta() != null ? s.getRuta().getNombre() : null,
                s.getTipoActividad() != null ? s.getTipoActividad().name() : null,
                s.getPublicoObjetivo() != null ? s.getPublicoObjetivo().getNombre() : null,
                s.getFormatoSalida() != null ? s.getFormatoSalida().getNombre() : null,
                s.getNivelMinimoRequerido() != null ? s.getNivelMinimoRequerido().getNombre() : null,
                s.getCapacidadMaxima(),
                totalInscritos,
                s.isInscripcionesCerradas(),
                s.getEstado(),
                s.getMotivoCancelacion(),
                tieneInforme,
                s.getCreatedAt()
        );
    }

    /**
     * Construye el DTO de un participante.
     *
     * @param p           entidad participante
     * @param dignidades  dignidades asignadas (ya cargadas para evitar N+1)
     * @param nivelMinimo nivel mínimo requerido por la salida (nullable)
     */
    private ParticipanteResponse toParticipanteResponse(
            SalidaParticipante p,
            List<SalidaParticipanteDignidad> dignidades,
            ClasificacionSocio nivelMinimo) {

        List<DignidadAsignadaResponse> dignidadesResp = dignidades.stream()
                .map(d -> new DignidadAsignadaResponse(
                        d.getId(), d.getDignidad().getId(), d.getDignidad().getNombre()))
                .toList();

        boolean esJefeSalida = dignidades.stream()
                .anyMatch(d -> "Jefe de Salida".equals(d.getDignidad().getNombre()));

        ClasificacionSocio nivelSocio = p.getSocio().getNivelTecnico();

        boolean nivelInsuficiente = nivelMinimo != null
                && (nivelSocio == null || nivelSocio.getNivel() < nivelMinimo.getNivel());

        return new ParticipanteResponse(
                p.getId(),
                p.getSocio().getId(),
                p.getSocio().getNombre(),
                p.getSocio().getApellido(),
                p.getEstadoInscripcion(),
                esJefeSalida,
                dignidadesResp,
                nivelSocio  != null ? nivelSocio.getId()     : null,
                nivelSocio  != null ? nivelSocio.getNombre() : null,
                nivelMinimo != null ? nivelMinimo.getId()     : null,
                nivelMinimo != null ? nivelMinimo.getNombre() : null,
                nivelInsuficiente,
                p.getRiesgoAprobadoPorDirectivo(),
                resolverNombreAprobador(p.getRiesgoAprobadoPorDirectivo()),
                p.getRiesgoAprobadoPorJefe(),
                resolverNombreAprobador(p.getRiesgoAprobadoPorJefe()),
                p.getRiesgoAprobadoEn(),
                p.getMotivoDirectivo(),
                p.getMotivoJefe(),
                p.getCreatedAt(),
                p.getSocio().getCedula(),
                p.getSocio().getTelefono(),
                p.getSocio().calcularEdad()
        );
    }
}
