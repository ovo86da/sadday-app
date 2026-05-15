package com.sadday.app.informes.service;

import com.sadday.app.informes.dto.*;
import com.sadday.app.informes.entity.DondeAutos;
import com.sadday.app.informes.entity.InformeSalida;
import com.sadday.app.informes.entity.InformeSalidaReconocimiento;
import com.sadday.app.informes.entity.SegmentoViaje;
import com.sadday.app.informes.entity.TipoTransporte;
import com.sadday.app.informes.repository.InformeSalidaReconocimientoRepository;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.mountains.entity.Contacto;
import com.sadday.app.mountains.entity.ContactoRuta;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.repository.ContactoRepository;
import com.sadday.app.mountains.repository.ContactoRutaRepository;
import com.sadday.app.salidas.entity.Dignidad;
import com.sadday.app.salidas.entity.EstadoSalida;
import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.salidas.entity.SalidaParticipante;
import com.sadday.app.salidas.entity.SalidaParticipanteDignidad;
import com.sadday.app.salidas.repository.DignidadRepository;
import com.sadday.app.salidas.repository.SalidaParticipanteDignidadRepository;
import com.sadday.app.salidas.repository.SalidaParticipanteRepository;
import com.sadday.app.salidas.repository.SalidaRepository;
import com.sadday.app.security.audit.AuditAspect.Auditable;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio del módulo Informes de Salida.
 *
 * <p>Reglas de acceso:
 * <ul>
 *   <li>Crear / Actualizar: Jefe de Salida designado para esa salida, o Admin/Directivo/Secretaria.</li>
 *   <li>Validar (firma del "Jefe de Montaña"): solo Admin o Directivo.</li>
 *   <li>Agregar / Eliminar reconocimientos: Jefe de Salida o Admin/Directivo/Secretaria.</li>
 *   <li>Consultar: cualquier usuario autenticado.</li>
 * </ul>
 *
 * <p>Un informe no puede modificarse una vez validado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InformeService {

    private final InformeSalidaRepository                informeRepository;
    private final InformeSalidaReconocimientoRepository  reconocimientoRepository;
    private final SalidaRepository                       salidaRepository;
    private final SalidaParticipanteRepository           participanteRepository;
    private final SalidaParticipanteDignidadRepository   dignidadAsignadaRepository;
    private final DignidadRepository                     dignidadRepository;
    private final ContactoRepository                     contactoRepository;
    private final ContactoRutaRepository                 contactoRutaRepository;
    private final SocioRepository                        socioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // =========================================================================
    // Crear
    // =========================================================================

    @Auditable(accion = "CREAR_INFORME", entidad = "informe_salida", detalle = "Informe de salida creado")
    @PreAuthorize("isAuthenticated()")
    public InformeResponse crear(UUID salidaId, CreateInformeRequest request, UUID currentUserId) {
        if (informeRepository.existsBySalidaId(salidaId)) {
            throw new BusinessException(ErrorCode.INFORME_ALREADY_EXISTS);
        }

        Salida salida = salidaRepository.findById(salidaId)
                .filter(s -> !s.isEliminada())
                .orElseThrow(() -> new BusinessException(ErrorCode.SALIDA_NOT_FOUND));

        if (salida.getEstado() != EstadoSalida.EN_CURSO && salida.getEstado() != EstadoSalida.REALIZADA) {
            throw new BusinessException(ErrorCode.SALIDA_NOT_PLANIFICADA,
                    "La salida debe estar EN_CURSO o REALIZADA para crear un informe");
        }

        verificarAutorizacionInforme(salidaId, currentUserId);

        Contacto contactoGuia   = resolverContacto(request.alquiloGuia(), request.contactoGuiaId(), "guía");
        Contacto contactoRefugio = resolverContacto(request.alquiloRefugio(), request.contactoRefugioId(), "refugio");
        Contacto contactoCamping = resolverContacto(request.acampo(), request.contactoCampingId(), "camping");

        InformeSalida informe = InformeSalida.builder()
                .salida(salida)
                .seRealizo(request.seRealizo())
                .lograronCumbre(request.lograronCumbre())
                .condicionesMeterologicas(strip(request.condicionesMeterologicas()))
                .horaSalidaClub(request.horaSalidaClub())
                .horaLlegadaMontana(request.horaLlegadaMontana())
                .horaCumbre(request.horaCumbre())
                .horaInicioDescenso(request.horaInicioDescenso())
                .horaLlegadaAutos(request.horaLlegadaAutos())
                .horaRegresoClub(request.horaRegresoClub())
                .cronica(strip(request.cronica()))
                .observaciones(strip(request.observaciones()))
                .comentariosVarios(strip(request.comentariosVarios()))
                .alquiloGuia(request.alquiloGuia())
                .costoGuia(request.costoGuia())
                .contactoGuia(contactoGuia)
                .alquiloRefugio(request.alquiloRefugio())
                .nombreRefugio(strip(request.nombreRefugio()))
                .costoRefugio(request.costoRefugio())
                .contactoRefugio(contactoRefugio)
                .acampo(request.acampo())
                .nombreCamping(strip(request.nombreCamping()))
                .costoCamping(request.costoCamping())
                .contactoCamping(contactoCamping)
                .dondeAutos(parseDondeAutos(request.dondeAutos()))
                .autosDescripcion(strip(request.autosDescripcion()))
                .autosLinkUbicacion(strip(request.autosLinkUbicacion()))
                .costoParqueadero(request.costoParqueadero())
                .costoTotal(request.costoTotal())
                .costoPorPersona(request.costoPorPersona())
                .build();

        informeRepository.save(informe);

        // Construir segmentos de viaje
        buildSegmentos(informe, request.segmentos());

        // Vincular contactos usados en el informe a la ruta (para "Usado como")
        vincularContactosARuta(informe);

        // Asignar dignidad al guía socio si no se alquiló externamente
        if (!request.alquiloGuia() && request.guiaSocioId() != null) {
            asignarDignidad(salidaId, request.guiaSocioId(), "Guía");
        }

        log.info("Informe creado para salida {}", salidaId);

        return toResponse(informe, List.of());
    }

    // =========================================================================
    // Obtener
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Optional<InformeResponse> obtener(UUID salidaId) {
        if (!salidaRepository.existsById(salidaId))
            throw new BusinessException(ErrorCode.SALIDA_NOT_FOUND);

        return informeRepository.findBySalidaId(salidaId)
                .map(informe -> {
                    List<InformeSalidaReconocimiento> reconocimientos =
                            reconocimientoRepository.findByInformeId(informe.getId());
                    return toResponse(informe, reconocimientos);
                });
    }

    // =========================================================================
    // Actualizar
    // =========================================================================

    @Auditable(accion = "ACTUALIZAR_INFORME", entidad = "informe_salida", detalle = "Informe de salida actualizado")
    @PreAuthorize("isAuthenticated()")
    public InformeResponse actualizar(UUID salidaId, UpdateInformeRequest request, UUID currentUserId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));

        if (informe.isValidado()) {
            throw new BusinessException(ErrorCode.INFORME_VALIDATED);
        }

        verificarAutorizacionInforme(salidaId, currentUserId);

        if (request.seRealizo() != null)               informe.setSeRealizo(request.seRealizo());
        if (request.lograronCumbre() != null)          informe.setLograronCumbre(request.lograronCumbre());
        if (request.condicionesMeterologicas() != null) informe.setCondicionesMeterologicas(strip(request.condicionesMeterologicas()));
        if (request.horaSalidaClub() != null)           informe.setHoraSalidaClub(request.horaSalidaClub());
        if (request.horaLlegadaMontana() != null)       informe.setHoraLlegadaMontana(request.horaLlegadaMontana());
        if (request.horaCumbre() != null)               informe.setHoraCumbre(request.horaCumbre());
        if (request.horaInicioDescenso() != null)       informe.setHoraInicioDescenso(request.horaInicioDescenso());
        if (request.horaLlegadaAutos() != null)         informe.setHoraLlegadaAutos(request.horaLlegadaAutos());
        if (request.horaRegresoClub() != null)          informe.setHoraRegresoClub(request.horaRegresoClub());
        if (request.cronica() != null)                  informe.setCronica(strip(request.cronica()));
        if (request.observaciones() != null)            informe.setObservaciones(strip(request.observaciones()));
        if (request.comentariosVarios() != null)        informe.setComentariosVarios(strip(request.comentariosVarios()));

        // alquiloGuia es @NotNull en UpdateInformeRequest, siempre viene
        informe.setAlquiloGuia(request.alquiloGuia());
        if (request.costoGuia() != null)                informe.setCostoGuia(request.costoGuia());
        informe.setContactoGuia(resolverContacto(request.alquiloGuia(), request.contactoGuiaId(), "guía"));

        // Alojamiento — actualizamos solo si los booleans vienen
        if (request.alquiloRefugio() != null) {
            informe.setAlquiloRefugio(request.alquiloRefugio());
            informe.setNombreRefugio(strip(request.nombreRefugio()));
            if (request.costoRefugio() != null) informe.setCostoRefugio(request.costoRefugio());
            informe.setContactoRefugio(resolverContacto(request.alquiloRefugio(), request.contactoRefugioId(), "refugio"));
        }
        if (request.acampo() != null) {
            informe.setAcampo(request.acampo());
            informe.setNombreCamping(strip(request.nombreCamping()));
            if (request.costoCamping() != null) informe.setCostoCamping(request.costoCamping());
            informe.setContactoCamping(resolverContacto(request.acampo(), request.contactoCampingId(), "camping"));
        }

        // Si se proporcionan segmentos, reemplazar todos los existentes.
        // Se hace flush explícito después del clear() para que Hibernate envíe los DELETE
        // a la BD ANTES de los INSERT nuevos, evitando violar uq_segmento_orden.
        if (request.segmentos() != null && !request.segmentos().isEmpty()) {
            informe.getSegmentos().clear();
            entityManager.flush();
            buildSegmentos(informe, request.segmentos());
        }

        // Autos
        if (request.dondeAutos() != null) {
            informe.setDondeAutos(parseDondeAutos(request.dondeAutos()));
            informe.setAutosDescripcion(strip(request.autosDescripcion()));
            informe.setAutosLinkUbicacion(strip(request.autosLinkUbicacion()));
            informe.setCostoParqueadero(request.costoParqueadero());
        }

        // Costos
        informe.setCostoTotal(request.costoTotal());
        informe.setCostoPorPersona(request.costoPorPersona());

        // Vincular contactos usados en el informe a la ruta (para "Usado como")
        vincularContactosARuta(informe);

        // Asignar dignidades si aplica (append-only: registro histórico)
        if (!request.alquiloGuia() && request.guiaSocioId() != null) {
            asignarDignidad(salidaId, request.guiaSocioId(), "Guía");
        }

        List<InformeSalidaReconocimiento> reconocimientos =
                reconocimientoRepository.findByInformeId(informe.getId());

        return toResponse(informe, reconocimientos);
    }

    // =========================================================================
    // Validar (firma del Directivo)
    // =========================================================================

    @Auditable(accion = "VALIDAR_INFORME", entidad = "informe_salida", detalle = "Informe de salida validado por el Directivo")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public void validar(UUID salidaId, UUID currentUserId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));

        if (informe.isValidado()) {
            throw new BusinessException(ErrorCode.INFORME_VALIDATED);
        }

        Socio validador = socioRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        informe.setValidadoPor(validador);
        informe.setValidadoEn(LocalDateTime.now());
        log.info("Informe {} validado por socio {}", informe.getId(), currentUserId);
    }

    // =========================================================================
    // Reconocimientos
    // =========================================================================

    @Auditable(accion = "AGREGAR_RECONOCIMIENTO", entidad = "informe_salida_reconocimientos", detalle = "Reconocimiento agregado al informe de salida")
    @PreAuthorize("isAuthenticated()")
    public ReconocimientoResponse agregarReconocimiento(UUID salidaId,
                                                        AgregarReconocimientoRequest request,
                                                        UUID currentUserId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));

        verificarAutorizacionInforme(salidaId, currentUserId);

        if (!participanteRepository.existsBySalidaIdAndSocioId(salidaId, request.socioId())) {
            throw new BusinessException(ErrorCode.SOCIO_NOT_PARTICIPANT);
        }

        Socio socio = socioRepository.findById(request.socioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        Socio registrador = socioRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        InformeSalidaReconocimiento rec = InformeSalidaReconocimiento.builder()
                .informe(informe)
                .socio(socio)
                .tipo(request.tipo())
                .motivo(request.motivo())
                .registradoPor(registrador)
                .build();

        reconocimientoRepository.save(rec);
        return toReconocimientoResponse(rec);
    }

    @Auditable(accion = "ELIMINAR_RECONOCIMIENTO", entidad = "informe_salida_reconocimientos", detalle = "Reconocimiento eliminado del informe de salida")
    @PreAuthorize("isAuthenticated()")
    public void eliminarReconocimiento(UUID salidaId, Long reconocimientoId, UUID currentUserId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));

        InformeSalidaReconocimiento rec = reconocimientoRepository.findById(reconocimientoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECONOCIMIENTO_NOT_FOUND));

        if (!rec.getInforme().getId().equals(informe.getId())) {
            throw new BusinessException(ErrorCode.RECONOCIMIENTO_NOT_FOUND);
        }

        verificarAutorizacionInforme(salidaId, currentUserId);
        reconocimientoRepository.delete(rec);
    }

    // =========================================================================
    // Informes pendientes del jefe de salida actual
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<InformePendienteResponse> obtenerPendientesJefe(UUID socioId) {
        return informeRepository.findSalidasPendientesJefe(socioId.toString())
                .stream()
                .map(row -> new InformePendienteResponse(
                        UUID.fromString((String) row[0]),
                        (String) row[1],
                        (java.time.LocalDate) row[2]
                ))
                .toList();
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Resuelve un contacto global por ID.
     * Si {@code aplica=true} y se proporcionó un ID, lo carga.
     * Si {@code aplica=false} o contactoId es null, retorna null.
     */
    private Contacto resolverContacto(boolean aplica, Integer contactoId, String tipo) {
        if (!aplica || contactoId == null) return null;
        return contactoRepository.findById(contactoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Contacto de " + tipo + " no encontrado (id=" + contactoId + ")"));
    }

    /**
     * Construye los segmentos de viaje y los asocia al informe.
     * Establece el campo {@code orden} secuencialmente empezando en 1.
     */
    private void buildSegmentos(InformeSalida informe, List<SegmentoViajeRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        List<SegmentoViaje> segmentos = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            SegmentoViajeRequest req = requests.get(i);
            Contacto contacto = resolverContacto(
                    Boolean.TRUE.equals(req.alquiloTransporte()), req.contactoId(), "transporte");

            TipoTransporte tipoTransporte = null;
            if (req.tipoTransporte() != null && !req.tipoTransporte().isBlank()) {
                tipoTransporte = TipoTransporte.valueOf(req.tipoTransporte());
            }

            SegmentoViaje segmento = SegmentoViaje.builder()
                    .informe(informe)
                    .orden((short) (i + 1))
                    .origen(req.origen().strip())
                    .destino(req.destino().strip())
                    .alquiloTransporte(req.alquiloTransporte())
                    .tipoTransporte(tipoTransporte)
                    .costoIndividual(req.costoIndividual())
                    .contacto(contacto)
                    .build();
            segmentos.add(segmento);
        }
        informe.getSegmentos().addAll(segmentos);
    }

    /** Retorna null si el valor es null o blank, o el valor con strip aplicado. */
    private String strip(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * Convierte el string de dondeAutos al enum, ignorando valores desconocidos.
     */
    private DondeAutos parseDondeAutos(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return DondeAutos.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Valor inválido para dondeAutos: " + value);
        }
    }

    /**
     * Asigna una dignidad a un socio participante de la salida (idempotente).
     * Si el socio no es participante o la dignidad no existe, lanza excepción.
     */
    private void asignarDignidad(UUID salidaId, UUID socioId, String nombreDignidad) {
        SalidaParticipante participante = participanteRepository.findBySalidaIdAndSocioId(salidaId, socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_PARTICIPANT));

        Dignidad dignidad = dignidadRepository.findByNombre(nombreDignidad)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Dignidad '" + nombreDignidad + "' no encontrada en el sistema"));

        boolean yaAsignada = dignidadAsignadaRepository
                .existsByParticipanteIdAndDignidadId(participante.getId(), dignidad.getId());

        if (!yaAsignada) {
            SalidaParticipanteDignidad spd = SalidaParticipanteDignidad.builder()
                    .participante(participante)
                    .dignidad(dignidad)
                    .build();
            dignidadAsignadaRepository.save(spd);
            log.info("Dignidad '{}' asignada al socio {} en salida {}", nombreDignidad, socioId, salidaId);
        }
    }

    /**
     * Vincula automáticamente los contactos del informe a la ruta en contactos_rutas.
     * Operación idempotente: no crea duplicados si el vínculo ya existe.
     */
    private void vincularContactosARuta(InformeSalida informe) {
        Ruta ruta = informe.getSalida().getRuta();
        if (ruta == null) return;

        Set<String> existentes = contactoRutaRepository.findActivosByRutaId(ruta.getId())
                .stream()
                .map(cr -> cr.getContacto().getId() + ":" + cr.getTipoContacto())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        vincularSiNuevo(informe.getContactoGuia(),    ruta, "GUIA",      existentes);
        vincularSiNuevo(informe.getContactoRefugio(), ruta, "REFUGIO",   existentes);
        vincularSiNuevo(informe.getContactoCamping(), ruta, "CAMPING",   existentes);

        informe.getSegmentos().stream()
                .filter(s -> Boolean.TRUE.equals(s.getAlquiloTransporte()) && s.getContacto() != null)
                .forEach(s -> vincularSiNuevo(s.getContacto(), ruta, "TRANSPORTE", existentes));
    }

    private void vincularSiNuevo(Contacto contacto, Ruta ruta, String tipo, Set<String> existentes) {
        if (contacto == null) return;
        String key = contacto.getId() + ":" + tipo;
        if (existentes.contains(key)) return;
        contactoRutaRepository.save(ContactoRuta.builder()
                .contacto(contacto)
                .ruta(ruta)
                .tipoContacto(tipo)
                .activo(true)
                .build());
        existentes.add(key);
    }

    /**
     * Verifica que el usuario actual sea Jefe de Salida de la salida indicada,
     * o tenga un rol privilegiado (Admin / Directivo / Secretaria).
     */
    private void verificarAutorizacionInforme(UUID salidaId, UUID currentUserId) {
        if (tieneRolPrivilegiado()) return;
        boolean esJefe = dignidadAsignadaRepository
                .countBySalidaSocioYDignidad(salidaId, currentUserId, "Jefe de Salida") > 0;
        if (esJefe) return;
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    private boolean tieneRolPrivilegiado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_DIRECTIVO") || a.equals("ROLE_SECRETARIA"));
    }

    private InformeResponse toResponse(InformeSalida informe,
                                       List<InformeSalidaReconocimiento> reconocimientos) {
        Socio validador      = informe.getValidadoPor();
        Contacto cGuia       = informe.getContactoGuia();
        Contacto cRefugio    = informe.getContactoRefugio();
        Contacto cCamping    = informe.getContactoCamping();

        List<SegmentoViajeResponse> segmentosResp = informe.getSegmentos().stream()
                .map(this::toSegmentoResponse)
                .toList();

        return new InformeResponse(
                informe.getId(),
                informe.getSalida().getId(),
                informe.getSalida().getNombre(),
                informe.getSeRealizo(),
                informe.getLograronCumbre(),
                informe.getCondicionesMeterologicas(),
                informe.getHoraSalidaClub(),
                informe.getHoraLlegadaMontana(),
                informe.getHoraCumbre(),
                informe.getHoraInicioDescenso(),
                informe.getHoraLlegadaAutos(),
                informe.getHoraRegresoClub(),
                informe.getCronica(),
                informe.getObservaciones(),
                informe.getComentariosVarios(),
                segmentosResp,
                informe.getAlquiloGuia(),
                informe.getCostoGuia(),
                cGuia != null ? cGuia.getId() : null,
                cGuia != null ? cGuia.getNombre() : null,
                cGuia != null ? cGuia.getTelefono() : null,
                cGuia != null ? cGuia.getCorreo() : null,
                informe.getAlquiloRefugio(),
                informe.getNombreRefugio(),
                informe.getCostoRefugio(),
                cRefugio != null ? cRefugio.getId() : null,
                cRefugio != null ? cRefugio.getNombre() : null,
                informe.getAcampo(),
                informe.getNombreCamping(),
                informe.getCostoCamping(),
                cCamping != null ? cCamping.getId() : null,
                cCamping != null ? cCamping.getNombre() : null,
                informe.getDondeAutos() != null ? informe.getDondeAutos().name() : null,
                informe.getAutosDescripcion(),
                informe.getAutosLinkUbicacion(),
                informe.getCostoParqueadero(),
                informe.getCostoTotal(),
                informe.getCostoPorPersona(),
                validador != null ? validador.getId() : null,
                validador != null ? validador.getNombre() + " " + validador.getApellido() : null,
                informe.getValidadoEn(),
                informe.getDocumento() != null ? informe.getDocumento().getId() : null,
                informe.getDocumento() != null ? informe.getDocumento().getFilename() : null,
                reconocimientos.stream().map(this::toReconocimientoResponse).toList(),
                informe.getCreatedAt(),
                informe.getUpdatedAt()
        );
    }

    private SegmentoViajeResponse toSegmentoResponse(SegmentoViaje s) {
        Contacto c = s.getContacto();
        return new SegmentoViajeResponse(
                s.getId(),
                s.getOrden(),
                s.getOrigen(),
                s.getDestino(),
                s.getAlquiloTransporte(),
                s.getTipoTransporte() != null ? s.getTipoTransporte().name() : null,
                s.getCostoIndividual(),
                c != null ? c.getId() : null,
                c != null ? c.getNombre() : null,
                c != null ? c.getTelefono() : null
        );
    }

    private ReconocimientoResponse toReconocimientoResponse(InformeSalidaReconocimiento rec) {
        Socio registrador = rec.getRegistradoPor();
        return new ReconocimientoResponse(
                rec.getId(),
                rec.getSocio().getId(),
                rec.getSocio().getNombre(),
                rec.getSocio().getApellido(),
                rec.getTipo(),
                rec.getMotivo(),
                registrador.getId(),
                registrador.getNombre() + " " + registrador.getApellido(),
                rec.getCreatedAt()
        );
    }
}
