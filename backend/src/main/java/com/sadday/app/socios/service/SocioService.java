package com.sadday.app.socios.service;

import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.auth.service.EmailVerificationService;
import com.sadday.app.security.audit.AuditAspect.Auditable;
import com.sadday.app.socios.dto.*;
import com.sadday.app.socios.entity.*;
import com.sadday.app.socios.repository.*;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio principal del módulo Socios.
 *
 * <p>Operaciones implementadas:
 * <ul>
 *   <li>Pre-registro: Secretaria envía invitación con cédula/correo; el socio completa el resto.</li>
 *   <li>CRUD de socios existentes (Admin / Secretaria)</li>
 *   <li>Habilitar / Inhabilitar (Admin / Secretaria / Directivo)</li>
 *   <li>Cambio de rol del sistema (Admin / Secretaria)</li>
 *   <li>Consulta de lookup tables (todos los autenticados)</li>
 *   <li>Perfil propio del socio autenticado</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SocioService {

    private final SocioRepository                socioRepository;
    private final TipoSocioClubRepository        tipoSocioRepo;
    private final EstadoHabilitacionRepository   estadoHabRepo;
    private final RolSistemaRepository           rolSistemaRepo;
    private final ClasificacionSocioRepository   clasifSocioRepo;
    private final EstadoCuotaRepository          cuotaRepository;
    private final SocioHabilitacionLogRepository habilitacionLogRepo;
    private final EmailVerificationService       emailVerificationService;
    private final UsuarioAuthRepository          usuarioAuthRepository;

    // Nombres de estado de habilitación
    private static final String ESTADO_HABILITADO   = "Habilitado";
    private static final String ESTADO_INHABILITADO = "Inhabilitado";
    private static final String ESTADO_VITALICIO    = "Socio Vitalicio";

    // =========================================================================
    // Iniciar registro (pre-registro)
    // =========================================================================

    /**
     * Inicia el proceso de registro de un nuevo socio enviando una invitación por email.
     * No crea el registro en la tabla {@code socios} todavía — el socio completa sus
     * datos personales y credenciales al abrir el link de activación.
     */
    @Auditable(accion = "INICIAR_REGISTRO_SOCIO", entidad = "socios", detalle = "Invitación de registro enviada al nuevo socio")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public void crear(CreateSocioRequest request) {
        emailVerificationService.sendMinimalInvitation(
                request.cedula(), request.correo(), request.telefono());
        log.info("Pre-registro iniciado: cedula={}, correo={}", request.cedula(), request.correo());
    }

    // =========================================================================
    // Listar socios (paginado, con filtros opcionales)
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public Page<SocioSummaryResponse> listar(
            Short  rolId,
            Short  estadoId,
            Short  tipoId,
            String q,
            Pageable pageable
    ) {
        Specification<Socio> spec = buildSpec(rolId, estadoId, tipoId, q);
        var page = socioRepository.findAll(spec, pageable);
        var ids = page.getContent().stream().map(Socio::getId).toList();
        var conCuenta = ids.isEmpty() ? Set.<UUID>of() : usuarioAuthRepository.findSocioIdsWithAccount(ids);
        return page.map(s -> toSummaryResponse(s, conCuenta.contains(s.getId())));
    }

    // =========================================================================
    // Búsqueda mínima (accesible a todos los autenticados)
    // =========================================================================

    /**
     * Búsqueda liviana por nombre/apellido. Devuelve solo id, nombre, apellido.
     * Accesible a cualquier usuario autenticado para que el Jefe de Salida
     * pueda agregar participantes sin exponer datos personales.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<SocioMinimalResponse> buscarMinimal(String q, int size) {
        Specification<Socio> spec = buildSpec(null, null, null, q);
        org.springframework.data.domain.PageRequest pageable =
                org.springframework.data.domain.PageRequest.of(0, size,
                        org.springframework.data.domain.Sort.by("apellido"));
        return socioRepository.findAll(spec, pageable)
                .map(s -> new SocioMinimalResponse(s.getId(), s.getNombre(), s.getApellido()))
                .toList();
    }

    // =========================================================================
    // Obtener socio por ID
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SocioResponse obtener(UUID id) {
        return toResponse(findById(id));
    }

    // =========================================================================
    // Actualizar datos personales
    // =========================================================================

    @Auditable(accion = "UPDATE_SOCIO", entidad = "socios", detalle = "Datos del socio actualizados")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public SocioResponse actualizar(UUID id, UpdateSocioRequest request) {
        Socio socio = findById(id);
        validarUnicidadActualizacion(request.cedula(), request.correo(), id);

        TipoSocioClub tipo   = findTipoById(request.tipoSocioId());
        ClasificacionSocio nivel = resolverNivel(request.nivelTecnicoId());

        socio.setNombre(request.nombre());
        socio.setApellido(request.apellido());
        socio.setCedula(request.cedula());
        socio.setCorreo(request.correo());
        socio.setTelefono(request.telefono());
        socio.setDireccion(request.direccion());
        socio.setFechaNacimiento(request.fechaNacimiento());
        socio.setFechaIngreso(request.fechaIngreso());
        socio.setFechaSalida(request.fechaSalida());
        socio.setTipoSangre(request.tipoSangre());
        socio.setEmergencyContactName(request.emergencyContactName());
        socio.setEmergencyContactPhone(request.emergencyContactPhone());
        socio.setEmergencyContactDireccion(request.emergencyContactDireccion());
        socio.setEmergencyContactName2(request.emergencyContactName2());
        socio.setEmergencyContactPhone2(request.emergencyContactPhone2());
        socio.setEmergencyContactDireccion2(request.emergencyContactDireccion2());
        socio.setTipoSocio(tipo);
        socio.setNivelTecnico(nivel);

        return toResponse(socioRepository.save(socio));
    }

    // =========================================================================
    // Auto-edición del perfil por el propio socio
    // =========================================================================

    /**
     * Permite al socio autenticado actualizar su propia información de contacto.
     *
     * <p>Solo se pueden modificar: correo, teléfono, dirección, tipo de sangre
     * y contactos de emergencia. Los datos de identidad (nombre, cédula, fechas)
     * permanecen gestionados por Admin/Secretaria.
     */
    @Auditable(accion = "UPDATE_MI_PERFIL", entidad = "socios", detalle = "Perfil propio actualizado")
    @PreAuthorize("isAuthenticated()")
    public SocioResponse actualizarMiPerfil(UUID socioId, UpdateMiPerfilRequest request) {
        Socio socio = findById(socioId);

        if (request.correo() != null && !request.correo().isBlank()
                && !request.correo().equalsIgnoreCase(socio.getCorreo())) {
            if (socioRepository.existsByCorreoAndIdNot(request.correo(), socioId)) {
                throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                        "Ya existe otro socio con el correo: " + request.correo());
            }
            socio.setCorreo(request.correo());
        }

        if (request.telefono() != null)              socio.setTelefono(request.telefono());
        if (request.direccion() != null)             socio.setDireccion(request.direccion());
        if (request.tipoSangre() != null)            socio.setTipoSangre(request.tipoSangre());
        if (request.emergencyContactName() != null)        socio.setEmergencyContactName(request.emergencyContactName());
        if (request.emergencyContactPhone() != null)       socio.setEmergencyContactPhone(request.emergencyContactPhone());
        if (request.emergencyContactDireccion() != null)   socio.setEmergencyContactDireccion(request.emergencyContactDireccion());
        if (request.emergencyContactName2() != null)       socio.setEmergencyContactName2(request.emergencyContactName2());
        if (request.emergencyContactPhone2() != null)      socio.setEmergencyContactPhone2(request.emergencyContactPhone2());
        if (request.emergencyContactDireccion2() != null)  socio.setEmergencyContactDireccion2(request.emergencyContactDireccion2());

        return toResponse(socioRepository.save(socio));
    }

    // =========================================================================
    // Habilitar / Inhabilitar
    // =========================================================================

    @Auditable(accion = "HABILITAR_SOCIO", entidad = "socios", idArgName = "id", detalle = "Socio habilitado para participar en salidas")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public void habilitar(UUID id, UUID realizadoPorId) {
        Socio socio = findById(id);
        validarNoVitalicio(socio);
        EstadoHabilitacion estadoNuevo = findEstadoByNombre(ESTADO_HABILITADO);
        if (socio.getEstadoHabilitacion().getId().equals(estadoNuevo.getId())) return;
        EstadoHabilitacion estadoAnterior = socio.getEstadoHabilitacion();
        socio.setEstadoHabilitacion(estadoNuevo);
        registrarLog(socio, estadoAnterior, estadoNuevo, realizadoPorId);
        log.info("Socio habilitado: id={}", id);
    }

    @Auditable(accion = "INHABILITAR_SOCIO", entidad = "socios", idArgName = "id", detalle = "Socio inhabilitado")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public void inhabilitar(UUID id, UUID realizadoPorId) {
        Socio socio = findById(id);
        validarNoVitalicio(socio);
        String rol = socio.getRolSistema().getNombre().toUpperCase();
        if (rol.equals("ADMIN") || rol.equals("SECRETARIA")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se puede inhabilitar a un socio con rol " + socio.getRolSistema().getNombre());
        }
        EstadoHabilitacion estadoNuevo = findEstadoByNombre(ESTADO_INHABILITADO);
        if (socio.getEstadoHabilitacion().getId().equals(estadoNuevo.getId())) return;
        EstadoHabilitacion estadoAnterior = socio.getEstadoHabilitacion();
        socio.setEstadoHabilitacion(estadoNuevo);
        registrarLog(socio, estadoAnterior, estadoNuevo, realizadoPorId);
        log.info("Socio inhabilitado: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<HabilitacionLogResponse> listarHabilitacionLog(UUID socioId) {
        findById(socioId); // verifica que existe
        return habilitacionLogRepo.findBySocioIdOrderByCambiadoEnDesc(socioId)
                .stream()
                .map(HabilitacionLogResponse::from)
                .toList();
    }

    private void validarNoVitalicio(Socio socio) {
        if (ESTADO_VITALICIO.equals(socio.getEstadoHabilitacion().getNombre())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Un Socio Vitalicio no puede ser habilitado ni inhabilitado");
        }
    }

    private void registrarLog(Socio socio, EstadoHabilitacion anterior,
                              EstadoHabilitacion nuevo, UUID realizadoPorId) {
        Socio realizadoPor = socioRepository.findById(realizadoPorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Socio ejecutor no encontrado"));
        habilitacionLogRepo.save(SocioHabilitacionLog.builder()
                .socio(socio)
                .estadoAnterior(anterior)
                .estadoNuevo(nuevo)
                .cambiadoPor(realizadoPor)
                .cambiadoEn(OffsetDateTime.now(ZoneOffset.UTC))
                .fuente("MANUAL")
                .build());
    }

    // =========================================================================
    // Cambiar rol del sistema
    // =========================================================================

    @Auditable(accion = "CAMBIAR_ROL_SOCIO", entidad = "socios", idArgName = "id", detalle = "Rol del socio en el sistema actualizado")
    @PreAuthorize("hasRole('ADMIN')")
    public void cambiarRol(UUID id, UpdateRolRequest request) {
        Socio socio       = findById(id);
        RolSistema rolNuevo = findRolById(request.rolSistemaId());
        String rolActual  = socio.getRolSistema().getNombre();

        // No permitir quitar el rol Admin si es el último
        if ("Admin".equalsIgnoreCase(rolActual) && !"Admin".equalsIgnoreCase(rolNuevo.getNombre())) {
            long adminsActivos = socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Admin", "ACTIVE");
            if (adminsActivos <= 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "No se puede cambiar el rol del único Admin activo.");
            }
        }

        // No permitir quitar el rol Secretaria si es la última activa
        if ("Secretaria".equalsIgnoreCase(rolActual) && !"Secretaria".equalsIgnoreCase(rolNuevo.getNombre())) {
            long secretariasActivas = socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Secretaria", "ACTIVE");
            if (secretariasActivas <= 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "No se puede cambiar el rol de la única secretaria activa.");
            }
        }

        // Límite máximo de admins (3) y secretarias (2)
        if ("Admin".equalsIgnoreCase(rolNuevo.getNombre()) && !"Admin".equalsIgnoreCase(rolActual)) {
            long totalAdmins = socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Admin", "ACTIVE");
            if (totalAdmins >= 3) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "El sistema permite un máximo de 3 admins activos.");
            }
        }
        if ("Secretaria".equalsIgnoreCase(rolNuevo.getNombre()) && !"Secretaria".equalsIgnoreCase(rolActual)) {
            long totalSecretarias = socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Secretaria", "ACTIVE");
            if (totalSecretarias >= 2) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "El sistema permite un máximo de 2 secretarias activas.");
            }
        }

        socio.setRolSistema(rolNuevo);
        log.info("Rol cambiado: socio_id={}, {} → {}", id, rolActual, rolNuevo.getNombre());
    }

    // =========================================================================
    // Actualizar nivel técnico (Admin / Secretaria / Directivo)
    // =========================================================================

    @Auditable(accion = "UPDATE_NIVEL_TECNICO", entidad = "socios", idArgName = "id", detalle = "Nivel técnico del socio actualizado")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public SocioResponse actualizarNivelTecnico(UUID id, UpdateNivelTecnicoRequest request) {
        Socio socio = findById(id);
        socio.setNivelTecnico(resolverNivel(request.nivelTecnicoId()));
        log.info("Nivel técnico actualizado: socio_id={}, nivel={}", id, request.nivelTecnicoId());
        return toResponse(socioRepository.save(socio));
    }

    // =========================================================================
    // Eliminar (Admin only — hard delete)
    // =========================================================================

    @Auditable(accion = "DELETE_SOCIO", entidad = "socios", detalle = "Socio eliminado del sistema")
    @PreAuthorize("hasRole('ADMIN')")
    public void eliminar(UUID id) {
        Socio socio = findById(id);
        socioRepository.delete(socio);
        log.info("Socio eliminado: id={}", id);
    }

    // =========================================================================
    // Cuotas (historial de pagos)
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public List<CuotaResponse> listarCuotas(UUID socioId) {
        findById(socioId); // valida que el socio existe
        return cuotaRepository.findBySocioIdOrderByFechaDesc(socioId)
                .stream()
                .map(this::toCuotaResponse)
                .collect(Collectors.toList());
    }

    @Auditable(accion = "CREATE_CUOTA", entidad = "estado_cuotas", detalle = "Cuota registrada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public CuotaResponse registrarCuota(UUID socioId, CreateCuotaRequest request, UUID registradoPorId) {
        Socio socio         = findById(socioId);
        Socio registradoPor = findById(registradoPorId);

        EstadoCuota cuota = EstadoCuota.builder()
                .socio(socio)
                .valor(request.valor())
                .fecha(request.fecha())
                .estado(request.estado())
                .registradoPor(registradoPor)
                .build();

        return toCuotaResponse(cuotaRepository.save(cuota));
    }

    @Auditable(accion = "DELETE_CUOTA", entidad = "estado_cuotas", detalle = "Cuota eliminada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public void eliminarCuota(UUID socioId, Long cuotaId) {
        EstadoCuota cuota = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Registro de cuota no encontrado: " + cuotaId));

        if (!cuota.getSocio().getId().equals(socioId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "El registro de cuota no pertenece al socio indicado");
        }

        cuotaRepository.delete(cuota);
    }

    // =========================================================================
    // Lookup tables
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public LookupsResponse obtenerLookups() {
        var tipos = tipoSocioRepo.findAll().stream()
                .map(t -> new LookupsResponse.LookupItem<>(t.getId(), t.getNombre(), t.getDescripcion()))
                .toList();

        var estados = estadoHabRepo.findAll().stream()
                .map(e -> new LookupsResponse.LookupItem<>(e.getId(), e.getNombre(), e.getDescripcion()))
                .toList();

        var roles = rolSistemaRepo.findAll().stream()
                .map(r -> new LookupsResponse.LookupItem<>(r.getId(), r.getNombre(), r.getDescripcion()))
                .toList();

        var clasifs = clasifSocioRepo.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(c -> c.getNivel()))
                .map(c -> new LookupsResponse.ClasificacionItem(c.getId(), c.getNivel(), c.getNombre(), c.getDescripcion()))
                .toList();

        return new LookupsResponse(tipos, estados, roles, clasifs);
    }

    // =========================================================================
    // Helpers privados — resolvers
    // =========================================================================

    private ClasificacionSocio resolverNivel(String id) {
        if (id == null || id.isBlank()) return null;
        return clasifSocioRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Nivel técnico no encontrado: " + id));
    }

    private EstadoHabilitacion findEstadoByNombre(String nombre) {
        return estadoHabRepo.findByNombre(nombre)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Estado de habilitación no encontrado: " + nombre));
    }

    private TipoSocioClub findTipoById(Short id) {
        return tipoSocioRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Tipo de socio no encontrado: " + id));
    }

    private RolSistema findRolById(Short id) {
        return rolSistemaRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Rol del sistema no encontrado: " + id));
    }

    private Socio findById(UUID id) {
        return socioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
    }

    // =========================================================================
    // Helpers privados — validaciones
    // =========================================================================

    private void validarUnicidadActualizacion(String cedula, String correo, UUID idActual) {
        if (socioRepository.existsByCedulaAndIdNot(cedula, idActual)) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                    "Ya existe otro socio con la cédula: " + cedula);
        }
        if (socioRepository.existsByCorreoAndIdNot(correo, idActual)) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                    "Ya existe otro socio con el correo: " + correo);
        }
    }

    // =========================================================================
    // Helpers privados — Specification para filtrado dinámico
    // =========================================================================

    /**
     * Construye una {@link Specification} combinando los filtros opcionales con AND.
     *
     * <p>Spring Data 4.x requiere que el {@code Specification} pasado a
     * {@code findAll(Specification, Pageable)} no sea null. Por eso se parte de
     * {@code cb.conjunction()} (siempre verdadero) y se van añadiendo predicados.
     */
    private Specification<Socio> buildSpec(Short rolId, Short estadoId, Short tipoId, String q) {
        // cb.conjunction() = "WHERE TRUE" — base segura para AND encadenados
        Specification<Socio> spec = (root, query, cb) -> cb.conjunction();

        if (rolId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("rolSistema").get("id"), rolId));
        }
        if (estadoId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("estadoHabilitacion").get("id"), estadoId));
        }
        if (tipoId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("tipoSocio").get("id"), tipoId));
        }
        if (q != null && !q.isBlank()) {
            String escaped = q.replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombre")), pattern),
                    cb.like(cb.lower(root.get("apellido")), pattern),
                    cb.like(cb.lower(root.get("cedula")), pattern),
                    cb.like(cb.lower(root.get("correo")), pattern)
            ));
        }
        return spec;
    }

    // =========================================================================
    // Helpers privados — mapeo a DTOs
    // =========================================================================

    private SocioResponse toResponse(Socio s) {
        return new SocioResponse(
                s.getId(),
                s.getNombre(),
                s.getApellido(),
                s.getCedula(),
                s.getCorreo(),
                s.getTelefono(),
                s.getDireccion(),
                s.getFechaNacimiento(),
                s.getFechaIngreso(),
                s.getFechaSalida(),
                s.getTipoSangre(),
                s.calcularEdad(),
                s.getEmergencyContactName(),
                s.getEmergencyContactPhone(),
                s.getEmergencyContactDireccion(),
                s.getEmergencyContactName2(),
                s.getEmergencyContactPhone2(),
                s.getEmergencyContactDireccion2(),
                s.getEstadoHabilitacion().getId(),
                s.getEstadoHabilitacion().getNombre(),
                s.getTipoSocio().getId(),
                s.getTipoSocio().getNombre(),
                s.getNivelTecnico() != null ? s.getNivelTecnico().getId()     : null,
                s.getNivelTecnico() != null ? s.getNivelTecnico().getNombre() : null,
                s.getRolSistema().getId(),
                s.getRolSistema().getNombre(),
                s.getEstadoAcceso().getId(),
                s.getEstadoAcceso().getCodigo(),
                s.isEsJefeMontana(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    private CuotaResponse toCuotaResponse(EstadoCuota c) {
        String registradoPorNombre = c.getRegistradoPor() != null
                ? c.getRegistradoPor().getNombre() + " " + c.getRegistradoPor().getApellido()
                : null;
        return new CuotaResponse(
                c.getId(),
                c.getValor(),
                c.getFecha(),
                c.getEstado(),
                registradoPorNombre,
                c.getCreatedAt()
        );
    }

    // =========================================================================
    // Reenviar invitación
    // =========================================================================

    @Auditable(accion = "REENVIAR_INVITACION", entidad = "socios", detalle = "Invitación de registro reenviada")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public void reenviarInvitacion(UUID socioId) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        if (usuarioAuthRepository.existsBySocioId(socioId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Este socio ya activó su cuenta y no necesita una nueva invitación.");
        }

        emailVerificationService.sendInvitation(socioId, socio.getCorreo());
        log.info("Invitación reenviada para socio_id={}", socioId);
    }

    // =========================================================================

    private SocioSummaryResponse toSummaryResponse(Socio s, boolean tieneCuenta) {
        return new SocioSummaryResponse(
                s.getId(),
                s.getNombre(),
                s.getApellido(),
                s.getCedula(),
                s.getCorreo(),
                s.getTelefono(),
                s.getFechaIngreso(),
                s.calcularEdad(),
                s.getEstadoHabilitacion().getNombre(),
                s.getTipoSocio().getNombre(),
                s.getNivelTecnico() != null ? s.getNivelTecnico().getNombre() : null,
                s.getRolSistema().getNombre(),
                s.getEstadoAcceso().getCodigo(),
                tieneCuenta,
                s.isEsJefeMontana()
        );
    }

    // =========================================================================
    // Flag Jefe de Montaña
    // =========================================================================

    @Auditable(accion = "SET_JEFE_MONTANA", entidad = "socios")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public SocioResponse setJefeMontana(UUID socioId, boolean valor) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
        if (!"DIRECTIVO".equalsIgnoreCase(socio.getRolSistema().getNombre())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El flag Jefe de Montaña solo puede asignarse a socios con rol DIRECTIVO");
        }
        socio.setEsJefeMontana(valor);
        log.info("Flag JM {} para socio_id={}", valor ? "activado" : "desactivado", socioId);
        return toResponse(socioRepository.save(socio));
    }
}
