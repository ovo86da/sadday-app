package com.sadday.app.estadisticas.service;

import com.sadday.app.estadisticas.dto.*;
import com.sadday.app.informes.entity.InformeSalida;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.mountains.entity.Mountain;
import com.sadday.app.mountains.repository.MountainRepository;
import com.sadday.app.salidas.entity.EstadoInscripcion;
import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.salidas.entity.SalidaParticipante;
import com.sadday.app.salidas.entity.SalidaParticipanteDignidad;
import com.sadday.app.salidas.repository.SalidaParticipanteDignidadRepository;
import com.sadday.app.salidas.repository.SalidaParticipanteRepository;
import com.sadday.app.salidas.repository.SalidaRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de Estadísticas e Historial.
 *
 * <p>Combina datos de múltiples módulos para ofrecer:
 * <ul>
 *   <li>Historial completo de un socio: salidas, cumbres, dignidades.</li>
 *   <li>Estadísticas de una montaña: salidas realizadas, rutas más transitadas.</li>
 * </ul>
 *
 * <p>Todos los datos son de solo lectura. No se modifican entidades aquí.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EstadisticaService {

    private final SocioRepository                     socioRepository;
    private final SalidaParticipanteRepository        participanteRepository;
    private final SalidaParticipanteDignidadRepository dignidadRepository;
    private final SalidaRepository                    salidaRepository;
    private final InformeSalidaRepository             informeRepository;
    private final MountainRepository                  mountainRepository;
    private final JdbcClient                          jdbcClient;

    // =========================================================================
    // Historial y estadísticas de un socio
    // =========================================================================

    /**
     * Historial completo de un socio: salidas, cumbres logradas y dignidades.
     *
     * <p>Cualquier usuario autenticado puede ver el historial de cualquier socio.
     */
    @PreAuthorize("isAuthenticated()")
    public SocioHistorialResponse obtenerHistorialSocio(UUID socioId, UUID currentUserId) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        // 1. Participaciones no canceladas (salida + ruta + mountain precargados)
        List<SalidaParticipante> participaciones = participanteRepository
                .findBySocioIdAndEstadoInscripcionNotFetch(socioId, EstadoInscripcion.CANCELADO);

        if (participaciones.isEmpty()) {
            return new SocioHistorialResponse(
                    socio.getId(), socio.getNombre(), socio.getApellido(),
                    0, 0, 0, List.of(), List.of());
        }

        // 2. Batch load dignidades para todas las participaciones
        List<Long> participanteIds = participaciones.stream().map(SalidaParticipante::getId).toList();
        Map<Long, List<SalidaParticipanteDignidad>> dignidadesPorParticipante =
                dignidadRepository.findByParticipanteIdIn(participanteIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getParticipante().getId()));

        // 3. Batch load informes de las salidas
        List<UUID> salidaIds = participaciones.stream()
                .map(p -> p.getSalida().getId()).toList();
        Map<UUID, InformeSalida> informesPorSalida = informeRepository.findBySalidaIdIn(salidaIds).stream()
                .collect(Collectors.toMap(i -> i.getSalida().getId(), i -> i));

        // 4. Construir historial
        List<SalidaHistorialItem> historial = participaciones.stream()
                .sorted(Comparator.comparing(p -> p.getSalida().getFechaInicio(), Comparator.reverseOrder()))
                .map(p -> {
                    Salida salida = p.getSalida();
                    List<String> dignidadesNombres = dignidadesPorParticipante
                            .getOrDefault(p.getId(), List.of()).stream()
                            .map(d -> d.getDignidad().getNombre())
                            .toList();
                    InformeSalida informe = informesPorSalida.get(salida.getId());
                    boolean esJefe = dignidadesNombres.contains("Jefe de Salida");
                    return new SalidaHistorialItem(
                            p.getId(),
                            salida.getId(),
                            salida.getNombre(),
                            salida.getFechaInicio(),
                            salida.getRuta().getMountain() != null ? salida.getRuta().getMountain().getNombre() : null,
                            salida.getRuta().getMountain() != null ? salida.getRuta().getMountain().getAltitud() : null,
                            salida.getRuta().getNombre(),
                            p.getEstadoInscripcion(),
                            salida.getEstado(),
                            esJefe,
                            informe != null ? informe.getSeRealizo() : null,
                            salida.getHoraEncuentroClub(),
                            dignidadesNombres,
                            p.getRiesgoAprobadoPorDirectivo() != null,
                            p.getRiesgoAprobadoPorJefe() != null
                    );
                })
                .toList();

        // 5. Conteos de dignidades por tipo (para gráficas)
        List<SalidaParticipanteDignidad> todasDignidades =
                dignidadRepository.findByParticipante_SocioId(socioId);
        List<DignidadConteoItem> conteos = todasDignidades.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getDignidad().getNombre(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new DignidadConteoItem(e.getKey(), e.getValue()))
                .toList();

        // 6. Resumen
        long cumbres = historial.stream()
                .filter(h -> Boolean.TRUE.equals(h.seRealizo()))
                .count();
        long vecesJefe = historial.stream()
                .filter(SalidaHistorialItem::esJefeSalida)
                .count();

        return new SocioHistorialResponse(
                socio.getId(), socio.getNombre(), socio.getApellido(),
                participaciones.size(),
                (int) cumbres,
                (int) vecesJefe,
                historial,
                conteos
        );
    }

    // =========================================================================
    // Estadísticas de una montaña
    // =========================================================================

    /**
     * Estadísticas de salidas hacia una montaña: totales, realizadas, rutas.
     * Accesible a cualquier usuario autenticado.
     */
    @PreAuthorize("isAuthenticated()")
    public MountainEstadisticaResponse obtenerEstadisticasMountain(int mountainId) {
        Mountain mountain = mountainRepository.findById(mountainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOUNTAIN_NOT_FOUND));

        List<Salida> salidas = salidaRepository.findByRuta_MountainId(mountainId);

        if (salidas.isEmpty()) {
            return new MountainEstadisticaResponse(
                    mountain.getId(), mountain.getNombre(), mountain.getRegion(),
                    mountain.getAltitud(), 0, 0, null, List.of());
        }

        // Informes para saber cuáles se realizaron
        List<UUID> salidaIds = salidas.stream().map(Salida::getId).toList();
        Map<UUID, InformeSalida> informesPorSalida = informeRepository.findBySalidaIdIn(salidaIds).stream()
                .collect(Collectors.toMap(i -> i.getSalida().getId(), i -> i));

        long realizadas = informesPorSalida.values().stream()
                .filter(i -> Boolean.TRUE.equals(i.getSeRealizo()))
                .count();

        LocalDate ultimaSalida = salidas.stream()
                .map(Salida::getFechaInicio)
                .max(Comparator.naturalOrder())
                .orElse(null);

        // Agrupar por ruta
        List<MountainEstadisticaResponse.RutaEstadisticaItem> rutas = salidas.stream()
                .collect(Collectors.groupingBy(s -> s.getRuta().getId(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> {
                    Salida any = salidas.stream()
                            .filter(s -> s.getRuta().getId().equals(e.getKey())).findFirst()
                            .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_NOT_FOUND));
                    return new MountainEstadisticaResponse.RutaEstadisticaItem(
                            any.getRuta().getId(),
                            any.getRuta().getNombre(),
                            e.getValue().intValue());
                })
                .sorted(Comparator.comparingInt(MountainEstadisticaResponse.RutaEstadisticaItem::totalSalidas).reversed())
                .toList();

        return new MountainEstadisticaResponse(
                mountain.getId(), mountain.getNombre(), mountain.getRegion(),
                mountain.getAltitud(),
                salidas.size(),
                (int) realizadas,
                ultimaSalida,
                rutas
        );
    }

    // =========================================================================
    // Estadísticas de dashboard — salidas por mes
    // =========================================================================

    /**
     * Agrega salidas por mes/año para el rango de {@code meses} meses anteriores
     * a la fecha actual (inclusive el mes corriente).
     *
     * <p>Accesible a cualquier usuario autenticado. Los datos son totales del club
     * sin información personal.
     *
     * @param meses  número de meses hacia atrás (1–60); se fuerza a ese rango
     */
    @PreAuthorize("isAuthenticated()")
    public DashboardEstadisticasResponse obtenerDashboardEstadisticas(int meses) {
        int mesesSanitized = Math.max(1, Math.min(meses, 60));

        // ── Totales globales (sin límite de fecha) ────────────────────────
        record TotalesRow(long total, long realizadas, long canceladas, long enCurso, long planificadas) {}

        TotalesRow totales = jdbcClient.sql("""
                SELECT
                    COUNT(*)                                                         AS total,
                    COUNT(*) FILTER (WHERE estado = 'REALIZADA')                    AS realizadas,
                    COUNT(*) FILTER (WHERE estado = 'CANCELADA')                    AS canceladas,
                    COUNT(*) FILTER (WHERE estado = 'EN_CURSO')                    AS en_curso,
                    COUNT(*) FILTER (WHERE estado = 'PLANIFICADA')                 AS planificadas
                FROM salida
                """)
                .query((rs, rowNum) -> new TotalesRow(
                        rs.getLong("total"),
                        rs.getLong("realizadas"),
                        rs.getLong("canceladas"),
                        rs.getLong("en_curso"),
                        rs.getLong("planificadas")))
                .single();

        // ── Desglose mensual ──────────────────────────────────────────────
        List<DashboardEstadisticasResponse.SalidaMesItem> porMes = jdbcClient.sql("""
                SELECT
                    EXTRACT(YEAR  FROM fecha_inicio)::int  AS anio,
                    EXTRACT(MONTH FROM fecha_inicio)::int  AS mes,
                    COUNT(*)                                              AS total,
                    COUNT(*) FILTER (WHERE estado = 'REALIZADA')                    AS realizadas,
                    COUNT(*) FILTER (WHERE estado = 'CANCELADA')                    AS canceladas,
                    COUNT(*) FILTER (WHERE estado = 'EN_CURSO')                    AS en_curso,
                    COUNT(*) FILTER (WHERE estado = 'PLANIFICADA')                 AS planificadas
                FROM salida
                WHERE fecha_inicio >= DATE_TRUNC('month', CURRENT_DATE) - (:meses - 1) * INTERVAL '1 month'
                  AND fecha_inicio  < DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month'
                GROUP BY anio, mes
                ORDER BY anio, mes
                """)
                .param("meses", mesesSanitized)
                .query((rs, rowNum) -> new DashboardEstadisticasResponse.SalidaMesItem(
                        rs.getInt("anio"),
                        rs.getInt("mes"),
                        rs.getInt("total"),
                        rs.getInt("realizadas"),
                        rs.getInt("canceladas"),
                        rs.getInt("en_curso"),
                        rs.getInt("planificadas")))
                .list();

        return new DashboardEstadisticasResponse(
                (int) totales.total(),
                (int) totales.realizadas(),
                (int) totales.canceladas(),
                (int) totales.enCurso(),
                (int) totales.planificadas(),
                porMes
        );
    }

    // =========================================================================
    // Estadísticas del club (distribución de socios y dignidades)
    // =========================================================================

    /**
     * Estadísticas globales del club: distribución de socios por nivel técnico
     * y tipo de socio, y dignidades más usadas en salidas.
     */
    @PreAuthorize("isAuthenticated()")
    public ClubEstadisticasResponse obtenerClubEstadisticas() {

        // ── Totales de socios ─────────────────────────────────────────────
        record HabRow(int habilitados, int inhabilitados, int total) {}
        HabRow hab = jdbcClient.sql("""
                SELECT
                    COUNT(*) FILTER (WHERE eh.nombre = 'Habilitado')   AS habilitados,
                    COUNT(*) FILTER (WHERE eh.nombre != 'Habilitado')  AS inhabilitados,
                    COUNT(*)                                           AS total
                FROM socios s
                JOIN estado_habilitacion eh ON s.estado_habilitacion_id = eh.id
                """)
                .query((rs, n) -> new HabRow(
                        rs.getInt("habilitados"),
                        rs.getInt("inhabilitados"),
                        rs.getInt("total")))
                .single();

        int total = hab.total();

        // ── Por nivel técnico ─────────────────────────────────────────────
        List<ClubEstadisticasResponse.NivelTecnicoItem> porNivel = jdbcClient.sql("""
                SELECT
                    COALESCE(cs.nombre, 'Sin nivel') AS nombre,
                    COUNT(s.id)                      AS total
                FROM socios s
                LEFT JOIN clasificacion_socio cs ON s.nivel_tecnico_id = cs.id
                GROUP BY cs.nivel, cs.nombre
                ORDER BY cs.nivel NULLS LAST
                """)
                .query((rs, n) -> {
                    int cnt = rs.getInt("total");
                    double pct = total > 0 ? Math.round(cnt * 1000.0 / total) / 10.0 : 0.0;
                    return new ClubEstadisticasResponse.NivelTecnicoItem(
                            rs.getString("nombre"), cnt, pct);
                })
                .list();

        // ── Por tipo de socio ─────────────────────────────────────────────
        List<ClubEstadisticasResponse.TipoSocioItem> porTipo = jdbcClient.sql("""
                SELECT ts.nombre, COUNT(s.id) AS total
                FROM socios s
                JOIN tipo_socio_club ts ON s.tipo_socio_id = ts.id
                GROUP BY ts.nombre
                ORDER BY total DESC
                """)
                .query((rs, n) -> new ClubEstadisticasResponse.TipoSocioItem(
                        rs.getString("nombre"), rs.getInt("total")))
                .list();

        // ── Top dignidades en salidas ──────────────────────────────────────
        List<ClubEstadisticasResponse.DignidadGlobalItem> topDig = jdbcClient.sql("""
                SELECT
                    d.nombre,
                    COUNT(spd.id)                   AS asignaciones,
                    COUNT(DISTINCT sp.socio_id)      AS socios_unicos
                FROM salida_participante_dignidades spd
                JOIN dignidades d ON spd.dignidad_id = d.id
                JOIN salida_participantes sp ON spd.participante_id = sp.id
                WHERE sp.estado_inscripcion::text != 'CANCELADO'
                GROUP BY d.nombre
                ORDER BY asignaciones DESC
                """)
                .query((rs, n) -> new ClubEstadisticasResponse.DignidadGlobalItem(
                        rs.getString("nombre"),
                        rs.getLong("asignaciones"),
                        rs.getLong("socios_unicos")))
                .list();

        return new ClubEstadisticasResponse(
                total, hab.habilitados(), hab.inhabilitados(),
                porNivel, porTipo, topDig);
    }

    // =========================================================================
    // Rankings del club
    // =========================================================================

    /**
     * Rankings: top jefes de salida, top participantes y top por cada dignidad.
     *
     * @param top número máximo de entradas por ranking (1–50)
     */
    @PreAuthorize("isAuthenticated()")
    public ClubRankingsResponse obtenerRankings(int top) {
        int n = Math.max(1, Math.min(top, 50));

        // ── Top jefes de salida ───────────────────────────────────────────
        List<SocioRankingItem> topJefes = jdbcClient.sql("""
                SELECT sp.socio_id, s.nombre, s.apellido,
                       cs.nombre AS nivel_tecnico,
                       COUNT(*)  AS total
                FROM salida_participantes sp
                JOIN socios s ON sp.socio_id = s.id
                LEFT JOIN clasificacion_socio cs ON s.nivel_tecnico_id = cs.id
                JOIN salida_participante_dignidades spd ON spd.participante_id = sp.id
                JOIN dignidades d ON spd.dignidad_id = d.id
                WHERE d.nombre = 'Jefe de Salida'
                  AND sp.estado_inscripcion::text != 'CANCELADO'
                GROUP BY sp.socio_id, s.nombre, s.apellido, cs.nombre
                ORDER BY total DESC
                LIMIT :n
                """)
                .param("n", n)
                .query((rs, row) -> new SocioRankingItem(
                        rs.getObject("socio_id", UUID.class),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("nivel_tecnico"),
                        rs.getInt("total")))
                .list();

        // ── Top participaciones ───────────────────────────────────────────
        List<SocioRankingItem> topPart = jdbcClient.sql("""
                SELECT sp.socio_id, s.nombre, s.apellido,
                       cs.nombre AS nivel_tecnico,
                       COUNT(*)  AS total
                FROM salida_participantes sp
                JOIN socios s ON sp.socio_id = s.id
                LEFT JOIN clasificacion_socio cs ON s.nivel_tecnico_id = cs.id
                WHERE sp.estado_inscripcion::text != 'CANCELADO'
                GROUP BY sp.socio_id, s.nombre, s.apellido, cs.nombre
                ORDER BY total DESC
                LIMIT :n
                """)
                .param("n", n)
                .query((rs, row) -> new SocioRankingItem(
                        rs.getObject("socio_id", UUID.class),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("nivel_tecnico"),
                        rs.getInt("total")))
                .list();

        // ── Top por dignidad (CTE con window function) ────────────────────
        List<ClubRankingsResponse.DignidadRankingItem> topPorDig = jdbcClient.sql("""
                WITH ranked AS (
                    SELECT
                        d.nombre                               AS dignidad,
                        sp.socio_id,
                        s.nombre,
                        s.apellido,
                        cs.nombre                              AS nivel_tecnico,
                        COUNT(*)                               AS total,
                        ROW_NUMBER() OVER (
                            PARTITION BY d.nombre
                            ORDER BY COUNT(*) DESC
                        )                                      AS rn
                    FROM salida_participante_dignidades spd
                    JOIN dignidades d ON spd.dignidad_id = d.id
                    JOIN salida_participantes sp ON spd.participante_id = sp.id
                    JOIN socios s ON sp.socio_id = s.id
                    LEFT JOIN clasificacion_socio cs ON s.nivel_tecnico_id = cs.id
                    WHERE sp.estado_inscripcion::text != 'CANCELADO'
                    GROUP BY d.nombre, sp.socio_id, s.nombre, s.apellido, cs.nombre
                )
                SELECT * FROM ranked WHERE rn <= :n
                ORDER BY dignidad, rn
                """)
                .param("n", n)
                .query((rs, row) -> new Object[]{
                        rs.getString("dignidad"),
                        new SocioRankingItem(
                                rs.getObject("socio_id", UUID.class),
                                rs.getString("nombre"),
                                rs.getString("apellido"),
                                rs.getString("nivel_tecnico"),
                                rs.getInt("total"))
                })
                .list()
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        row -> (String) row[0],
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(
                                row -> (SocioRankingItem) row[1],
                                java.util.stream.Collectors.toList())))
                .entrySet().stream()
                .map(e -> new ClubRankingsResponse.DignidadRankingItem(e.getKey(), e.getValue()))
                .toList();

        // ── Por categoría ─────────────────────────────────────────────────
        List<CategoriaEstadisticaItem> porCategoria = jdbcClient.sql("""
                SELECT
                    sal.tipo_actividad,
                    COUNT(DISTINCT sal.id)      AS total_salidas,
                    COUNT(DISTINCT sp.socio_id) AS total_participantes
                FROM salida sal
                JOIN salida_participantes sp ON sp.salida_id = sal.id
                WHERE sp.estado_inscripcion::text != 'CANCELADO'
                  AND sal.tipo_actividad IS NOT NULL
                GROUP BY sal.tipo_actividad
                ORDER BY total_participantes DESC
                """)
                .query((rs, row) -> new CategoriaEstadisticaItem(
                        rs.getString("tipo_actividad"),
                        rs.getInt("total_salidas"),
                        rs.getInt("total_participantes")))
                .list();

        // ── Top por categoría y dignidad (Jefe de Salida / Guía) ─────────────
        List<ClubRankingsResponse.DignidadRankingItem> rankingsPorCat = jdbcClient.sql("""
                WITH ranked AS (
                    SELECT
                        sal.tipo_actividad,
                        d.nombre                               AS dignidad,
                        sp.socio_id,
                        s.nombre,
                        s.apellido,
                        cs.nombre                              AS nivel_tecnico,
                        COUNT(*)                               AS total,
                        ROW_NUMBER() OVER (
                            PARTITION BY sal.tipo_actividad, d.nombre
                            ORDER BY COUNT(*) DESC
                        )                                      AS rn
                    FROM salida_participante_dignidades spd
                    JOIN dignidades d ON spd.dignidad_id = d.id
                    JOIN salida_participantes sp ON spd.participante_id = sp.id
                    JOIN socios s ON sp.socio_id = s.id
                    LEFT JOIN clasificacion_socio cs ON s.nivel_tecnico_id = cs.id
                    JOIN salida sal ON sp.salida_id = sal.id
                    WHERE sp.estado_inscripcion::text != 'CANCELADO'
                      AND sal.tipo_actividad IS NOT NULL
                      AND d.nombre IN ('Jefe de Salida', 'Guía')
                    GROUP BY sal.tipo_actividad, d.nombre, sp.socio_id, s.nombre, s.apellido, cs.nombre
                )
                SELECT * FROM ranked WHERE rn <= :n
                ORDER BY tipo_actividad, dignidad, rn
                """)
                .param("n", n)
                .query((rs, row) -> new Object[]{
                        rs.getString("tipo_actividad") + "|" + rs.getString("dignidad"),
                        new SocioRankingItem(
                                rs.getObject("socio_id", UUID.class),
                                rs.getString("nombre"),
                                rs.getString("apellido"),
                                rs.getString("nivel_tecnico"),
                                rs.getInt("total"))
                })
                .list()
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        row -> (String) row[0],
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(
                                row -> (SocioRankingItem) row[1],
                                java.util.stream.Collectors.toList())))
                .entrySet().stream()
                .map(e -> new ClubRankingsResponse.DignidadRankingItem(e.getKey(), e.getValue()))
                .toList();

        List<CategoriaDignidadRankingItem> rankingsPorCategoria = rankingsPorCat.stream()
                .map(dr -> {
                    String[] parts = dr.dignidad().split("\\|", 2);
                    return new CategoriaDignidadRankingItem(parts[0], parts[1], dr.top());
                })
                .toList();

        return new ClubRankingsResponse(topJefes, topPart, topPorDig, porCategoria, rankingsPorCategoria);
    }

    // =========================================================================
    // Búsqueda avanzada de participantes
    // =========================================================================

    /**
     * Busca socios que han participado en salidas según los filtros dados.
     * Todos los parámetros son opcionales; si ninguno se indica, devuelve
     * todos los socios que hayan participado en al menos una salida.
     *
     * @param mountainId     ID de la montaña
     * @param rutaId         ID de la ruta
     * @param dignidadId     ID de la dignidad que debe haber tenido el socio
     * @param nivelTecnicoId ID de la clasificación técnica del socio
     * @param q              búsqueda por nombre / apellido (ILIKE)
     */
    @SuppressWarnings("java:S2077") // SQL dinámico seguro: estructura hardcodeada, valores via parámetros nombrados
    @PreAuthorize("isAuthenticated()")
    public List<ParticipanteFiltradoItem> buscarParticipantes(
            Integer mountainId,
            Integer rutaId,
            Integer dignidadId,
            String  nivelTecnicoId,
            String  tipoActividad,
            String  q
    ) {
        log.debug("buscarParticipantes: tipoActividad={} mountainId={} rutaId={} dignidadId={} nivelTecnicoId={} q={}",
                tipoActividad, mountainId, rutaId, dignidadId, nivelTecnicoId, q);

        var where  = new StringBuilder(" WHERE sp.estado_inscripcion::text != 'CANCELADO'");
        var params = new java.util.HashMap<String, Object>();

        if (tipoActividad != null && !tipoActividad.isBlank()) {
            where.append(" AND sal.tipo_actividad = :tipoActividad");
            params.put("tipoActividad", tipoActividad.toUpperCase());
        }
        if (mountainId != null) {
            where.append(" AND r.mountain_id = :mountainId");
            params.put("mountainId", mountainId);
        }
        if (rutaId != null) {
            where.append(" AND sal.ruta_id = :rutaId");
            params.put("rutaId", rutaId);
        }
        if (nivelTecnicoId != null && !nivelTecnicoId.isBlank()) {
            where.append(" AND s.nivel_tecnico_id::text = :nivelTecnicoId");
            params.put("nivelTecnicoId", nivelTecnicoId.trim());
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (s.nombre ILIKE :q OR s.apellido ILIKE :q OR s.cedula ILIKE :q)");
            params.put("q", "%" + q.trim() + "%");
        }
        if (dignidadId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM salida_participante_dignidades spd2" +
                         " WHERE spd2.participante_id = sp.id AND spd2.dignidad_id = :dignidadId)");
            params.put("dignidadId", dignidadId);
        }

        String sql = """
                SELECT
                    sp.socio_id,
                    s.nombre,
                    s.apellido,
                    cs.nombre                                          AS nivel_tecnico,
                    COUNT(DISTINCT sp.id)                             AS total_participaciones,
                    COUNT(DISTINCT spd_jefe.participante_id)          AS veces_jefe,
                    MAX(sal.fecha_inicio)                             AS ultima_participacion,
                    STRING_AGG(DISTINCT d.nombre, ', ')               AS dignidades_txt
                FROM salida_participantes sp
                JOIN socios s ON sp.socio_id = s.id
                LEFT JOIN clasificacion_socio cs ON s.nivel_tecnico_id = cs.id
                JOIN salida sal ON sp.salida_id = sal.id
                LEFT JOIN rutas r ON sal.ruta_id = r.id
                LEFT JOIN salida_participante_dignidades spd ON spd.participante_id = sp.id
                LEFT JOIN dignidades d ON spd.dignidad_id = d.id
                LEFT JOIN salida_participante_dignidades spd_jefe
                       ON spd_jefe.participante_id = sp.id
                      AND spd_jefe.dignidad_id = (
                              SELECT id FROM dignidades WHERE nombre = 'Jefe de Salida' LIMIT 1)
                """ + where + " " + """
                GROUP BY sp.socio_id, s.nombre, s.apellido, cs.nombre
                ORDER BY total_participaciones DESC
                LIMIT 300
                """;

        return jdbcClient.sql(sql)
                .params(params)
                .query((rs, row) -> {
                    String digTxt = rs.getString("dignidades_txt");
                    List<String> dignidades = (digTxt != null && !digTxt.isBlank())
                            ? List.of(digTxt.split(", "))
                            : List.of();
                    return new ParticipanteFiltradoItem(
                            rs.getObject("socio_id", UUID.class),
                            rs.getString("nombre"),
                            rs.getString("apellido"),
                            rs.getString("nivel_tecnico"),
                            rs.getInt("total_participaciones"),
                            rs.getInt("veces_jefe"),
                            dignidades,
                            rs.getObject("ultima_participacion", java.time.LocalDate.class));
                })
                .list();
    }

    // =========================================================================
    // Estadísticas de reuniones
    // =========================================================================

    /**
     * Rankings y métricas globales de asistencia a reuniones.
     *
     * @param top   número máximo de socios en cada ranking (1-50)
     * @param meses número de meses hacia atrás para el gráfico por mes (1-60)
     */
    @PreAuthorize("isAuthenticated()")
    public ReunionesRankingResponse obtenerRankingReuniones(int top, int meses) {
        int topN  = Math.max(1, Math.min(top, 50));
        int mesesN = Math.max(1, Math.min(meses, 60));

        // Total reuniones
        int totalReuniones = jdbcClient.sql("SELECT COUNT(*) FROM actas_reunion")
                .query(Integer.class).single();

        // Promedio global de asistentes por reunión
        double promedio = jdbcClient.sql("""
                SELECT COALESCE(AVG(cnt), 0) FROM (
                    SELECT COUNT(*) AS cnt FROM asistentes_reunion GROUP BY acta_id
                ) sub
                """).query(Double.class).single();

        // Top asistentes
        List<SocioRankingItem> topAsistentes = jdbcClient.sql("""
                SELECT s.id, s.nombre, s.apellido,
                       cs.nombre AS nivel_tecnico,
                       COUNT(ar.id) AS total
                FROM socios s
                JOIN asistentes_reunion ar ON ar.socio_id = s.id
                LEFT JOIN clasificacion_socio cs ON cs.id = s.nivel_tecnico_id
                GROUP BY s.id, s.nombre, s.apellido, cs.nombre
                ORDER BY total DESC
                LIMIT :top
                """)
                .param("top", topN)
                .query((rs, rowNum) -> new SocioRankingItem(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("nivel_tecnico"),
                        rs.getInt("total")))
                .list();

        // Menos asistentes (socios activos con al menos 1 reunión disponible)
        List<SocioRankingItem> menosAsistentes = jdbcClient.sql("""
                SELECT s.id, s.nombre, s.apellido,
                       cs.nombre AS nivel_tecnico,
                       COUNT(ar.id) AS total
                FROM socios s
                JOIN estado_acceso ea ON ea.id = s.estado_acceso_id
                LEFT JOIN asistentes_reunion ar ON ar.socio_id = s.id
                LEFT JOIN clasificacion_socio cs ON cs.id = s.nivel_tecnico_id
                WHERE ea.codigo NOT IN ('EX_MEMBER', 'PENDING_REGISTER')
                GROUP BY s.id, s.nombre, s.apellido, cs.nombre
                HAVING COUNT(ar.id) < (SELECT MAX(sub.cnt) FROM
                    (SELECT COUNT(*) AS cnt FROM asistentes_reunion GROUP BY acta_id) sub)
                ORDER BY total ASC
                LIMIT :top
                """)
                .param("top", topN)
                .query((rs, rowNum) -> new SocioRankingItem(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("nivel_tecnico"),
                        rs.getInt("total")))
                .list();

        // Asistencia por mes (últimos N meses)
        LocalDate desde = LocalDate.now().minusMonths(mesesN).withDayOfMonth(1);
        List<ReunionAsistenciaMesItem> porMes = jdbcClient.sql("""
                SELECT EXTRACT(YEAR  FROM a.fecha)::int AS anio,
                       EXTRACT(MONTH FROM a.fecha)::int AS mes,
                       COUNT(DISTINCT a.id)             AS total_reuniones,
                       COUNT(ar.id)                     AS total_asistencias,
                       ROUND(COUNT(ar.id)::numeric /
                             NULLIF(COUNT(DISTINCT a.id), 0), 2) AS promedio
                FROM actas_reunion a
                LEFT JOIN asistentes_reunion ar ON ar.acta_id = a.id
                WHERE a.fecha >= :desde
                GROUP BY anio, mes
                ORDER BY anio, mes
                """)
                .param("desde", desde)
                .query((rs, rowNum) -> new ReunionAsistenciaMesItem(
                        rs.getInt("anio"),
                        rs.getInt("mes"),
                        rs.getInt("total_reuniones"),
                        rs.getInt("total_asistencias"),
                        rs.getDouble("promedio")))
                .list();

        return new ReunionesRankingResponse(totalReuniones, promedio, topAsistentes, menosAsistentes, porMes);
    }

    /**
     * Actividad total de un socio: reuniones asistidas + salidas participadas.
     *
     * <p>El propio socio puede ver sus datos; Admin/Directivo/Secretaria pueden ver cualquiera.
     */
    @PreAuthorize("isAuthenticated()")
    public ActividadTotalSocioResponse obtenerActividadTotalSocio(UUID socioId, UUID currentUserId) {
        if (!socioId.equals(currentUserId) && !tieneRolPrivilegiado()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        // Reuniones asistidas
        List<ActividadTotalSocioResponse.ReunionAsistidaItem> reuniones = jdbcClient.sql("""
                SELECT a.id, a.fecha, a.numero_reunion, a.tipo_acta::text AS tipo_acta,
                       CASE WHEN p.id IS NOT NULL
                            THEN p.nombre || ' ' || p.apellido
                            ELSE NULL END AS presidente_nombre
                FROM asistentes_reunion ar
                JOIN actas_reunion a ON a.id = ar.acta_id
                LEFT JOIN socios p ON p.id = a.presidente_reunion_id
                WHERE ar.socio_id = :socioId
                ORDER BY a.fecha DESC
                """)
                .param("socioId", socioId)
                .query((rs, rowNum) -> new ActividadTotalSocioResponse.ReunionAsistidaItem(
                        UUID.fromString(rs.getString("id")),
                        rs.getDate("fecha").toLocalDate(),
                        rs.getObject("numero_reunion", Integer.class),
                        rs.getString("tipo_acta"),
                        rs.getString("presidente_nombre")))
                .list();

        // Salidas participadas (estado != CANCELADO)
        int totalSalidas = participanteRepository.countBySocioIdAndEstadoInscripcionNot(
                socioId, EstadoInscripcion.CANCELADO);

        // Cumbres logradas: salidas con informe realizado donde el socio está CONFIRMADO
        int cumbres = jdbcClient.sql("""
                SELECT COUNT(DISTINCT sp.salida_id)
                FROM salida_participantes sp
                JOIN informe_salida i ON i.salida_id = sp.salida_id
                WHERE sp.socio_id = :socioId
                  AND sp.estado_inscripcion = 'CONFIRMADO'
                  AND i.se_realizo = true
                """)
                .param("socioId", socioId)
                .query(Integer.class).single();

        return new ActividadTotalSocioResponse(
                socio.getId(),
                socio.getNombre(),
                socio.getApellido(),
                reuniones.size(),
                totalSalidas,
                (int) cumbres,
                reuniones
        );
    }

    // =========================================================================
    // Ranking Montaña & Rutas
    // =========================================================================

    /**
     * Top N montañas/rutas por salidas y participantes.
     *
     * @param top número de entradas por ranking (1–50)
     */
    @PreAuthorize("isAuthenticated()")
    public RankingMontanaRutaResponse obtenerRankingMontanaRuta(int top) {
        int n = Math.max(1, Math.min(top, 50));

        String mountainBaseSql = """
                SELECT m.id, m.nombre, m.region, m.altitud,
                       COUNT(DISTINCT s.id) AS total_salidas
                FROM mountains m
                LEFT JOIN rutas r   ON r.mountain_id = m.id
                LEFT JOIN salida s  ON s.ruta_id     = r.id
                GROUP BY m.id, m.nombre, m.region, m.altitud
                """;

        List<MontanaRankingItem> masSalidas = jdbcClient.sql(
                mountainBaseSql + " ORDER BY total_salidas DESC LIMIT :n")
                .param("n", n)
                .query((rs, row) -> new MontanaRankingItem(
                        rs.getInt("id"), rs.getString("nombre"),
                        rs.getString("region"), rs.getInt("altitud"),
                        rs.getInt("total_salidas")))
                .list();

        List<MontanaRankingItem> menosSalidas = jdbcClient.sql(
                mountainBaseSql + " HAVING COUNT(DISTINCT s.id) > 0 ORDER BY total_salidas ASC LIMIT :n")
                .param("n", n)
                .query((rs, row) -> new MontanaRankingItem(
                        rs.getInt("id"), rs.getString("nombre"),
                        rs.getString("region"), rs.getInt("altitud"),
                        rs.getInt("total_salidas")))
                .list();

        String rutaBaseSql = """
                SELECT r.id, r.nombre, r.tipo_actividad::text, m.nombre AS mountain_nombre,
                       COUNT(DISTINCT s.id)                                    AS total_salidas,
                       COUNT(DISTINCT sp.socio_id) FILTER (
                           WHERE sp.estado_inscripcion::text != 'CANCELADO')   AS total_participantes
                FROM rutas r
                LEFT JOIN mountains m ON r.mountain_id = m.id
                LEFT JOIN salida s    ON s.ruta_id      = r.id
                LEFT JOIN salida_participantes sp ON sp.salida_id = s.id
                GROUP BY r.id, r.nombre, r.tipo_actividad, m.nombre
                """;

        List<RutaRankingItem> rutasMasSalidas = jdbcClient.sql(
                rutaBaseSql + " HAVING COUNT(DISTINCT s.id) > 0 ORDER BY total_salidas DESC LIMIT :n")
                .param("n", n)
                .query((rs, row) -> new RutaRankingItem(
                        rs.getInt("id"), rs.getString("nombre"),
                        rs.getString("tipo_actividad"), rs.getString("mountain_nombre"),
                        rs.getInt("total_salidas"), rs.getInt("total_participantes")))
                .list();

        List<RutaRankingItem> rutasMenosSalidas = jdbcClient.sql(
                rutaBaseSql + " HAVING COUNT(DISTINCT s.id) > 0 ORDER BY total_salidas ASC LIMIT :n")
                .param("n", n)
                .query((rs, row) -> new RutaRankingItem(
                        rs.getInt("id"), rs.getString("nombre"),
                        rs.getString("tipo_actividad"), rs.getString("mountain_nombre"),
                        rs.getInt("total_salidas"), rs.getInt("total_participantes")))
                .list();

        List<RutaRankingItem> rutasMasParticipantes = jdbcClient.sql(
                rutaBaseSql + " HAVING COUNT(DISTINCT s.id) > 0 ORDER BY total_participantes DESC LIMIT :n")
                .param("n", n)
                .query((rs, row) -> new RutaRankingItem(
                        rs.getInt("id"), rs.getString("nombre"),
                        rs.getString("tipo_actividad"), rs.getString("mountain_nombre"),
                        rs.getInt("total_salidas"), rs.getInt("total_participantes")))
                .list();

        return new RankingMontanaRutaResponse(
                masSalidas, menosSalidas,
                rutasMasSalidas, rutasMenosSalidas,
                rutasMasParticipantes);
    }

    /**
     * Búsqueda libre de montañas o rutas con conteo de salidas y participantes.
     *
     * @param tipo        "montana" | "ruta" | "ambos"
     * @param q           texto libre (ILIKE sobre nombre)
     * @param sinSalidas  si true, devuelve solo los que tienen 0 salidas
     */
    @PreAuthorize("isAuthenticated()")
    public List<MontanaRutaBusquedaItem> buscarMontanaRuta(String tipo, String q, boolean sinSalidas) {
        List<MontanaRutaBusquedaItem> result = new java.util.ArrayList<>();

        boolean inclMontana = tipo == null || tipo.isBlank() || "montana".equalsIgnoreCase(tipo) || "ambos".equalsIgnoreCase(tipo);
        boolean inclRuta    = tipo == null || tipo.isBlank() || "ruta".equalsIgnoreCase(tipo)    || "ambos".equalsIgnoreCase(tipo);

        String qLike = (q != null && !q.isBlank()) ? "%" + q.trim() + "%" : null;

        if (inclMontana) {
            var sql = new StringBuilder("""
                    SELECT m.id, m.nombre, m.region,
                           COUNT(DISTINCT s.id) AS total_salidas,
                           COUNT(DISTINCT sp.socio_id) FILTER (
                               WHERE sp.estado_inscripcion::text != 'CANCELADO') AS total_participantes
                    FROM mountains m
                    LEFT JOIN rutas r   ON r.mountain_id = m.id
                    LEFT JOIN salida s  ON s.ruta_id     = r.id
                    LEFT JOIN salida_participantes sp ON sp.salida_id = s.id
                    WHERE 1=1
                    """);
            var params = new java.util.HashMap<String, Object>();
            if (qLike != null) {
                sql.append(" AND m.nombre ILIKE :q");
                params.put("q", qLike);
            }
            sql.append(" GROUP BY m.id, m.nombre, m.region");
            if (sinSalidas) sql.append(" HAVING COUNT(DISTINCT s.id) = 0");
            sql.append(" ORDER BY total_salidas DESC, m.nombre ASC LIMIT 100");

            jdbcClient.sql(sql.toString()).params(params)
                    .query((rs, row) -> new MontanaRutaBusquedaItem(
                            rs.getInt("id"), rs.getString("nombre"),
                            "MONTANA", null, null,
                            rs.getInt("total_salidas"), rs.getInt("total_participantes")))
                    .list()
                    .forEach(result::add);
        }

        if (inclRuta) {
            var sql = new StringBuilder("""
                    SELECT r.id, r.nombre, r.tipo_actividad::text, m.nombre AS mountain_nombre,
                           COUNT(DISTINCT s.id) AS total_salidas,
                           COUNT(DISTINCT sp.socio_id) FILTER (
                               WHERE sp.estado_inscripcion::text != 'CANCELADO') AS total_participantes
                    FROM rutas r
                    LEFT JOIN mountains m ON r.mountain_id = m.id
                    LEFT JOIN salida s    ON s.ruta_id     = r.id
                    LEFT JOIN salida_participantes sp ON sp.salida_id = s.id
                    WHERE 1=1
                    """);
            var params = new java.util.HashMap<String, Object>();
            if (qLike != null) {
                sql.append(" AND r.nombre ILIKE :q");
                params.put("q", qLike);
            }
            sql.append(" GROUP BY r.id, r.nombre, r.tipo_actividad, m.nombre");
            if (sinSalidas) sql.append(" HAVING COUNT(DISTINCT s.id) = 0");
            sql.append(" ORDER BY total_salidas DESC, r.nombre ASC LIMIT 100");

            jdbcClient.sql(sql.toString()).params(params)
                    .query((rs, row) -> new MontanaRutaBusquedaItem(
                            rs.getInt("id"), rs.getString("nombre"),
                            "RUTA", rs.getString("tipo_actividad"), rs.getString("mountain_nombre"),
                            rs.getInt("total_salidas"), rs.getInt("total_participantes")))
                    .list()
                    .forEach(result::add);
        }

        return result;
    }

    // =========================================================================
    // Búsqueda de actividad por período
    // =========================================================================

    @PreAuthorize("isAuthenticated()")
    public List<SalidaPeriodoItem> buscarSalidasEnPeriodo(
            LocalDate fechaDesde, LocalDate fechaHasta, String tipoActividad) {

        var params = new java.util.HashMap<String, Object>();
        params.put("desde", fechaDesde);
        params.put("hasta", fechaHasta);

        String tipoClause = "";
        if (tipoActividad != null && !tipoActividad.isBlank()) {
            tipoClause = "  AND s.tipo_actividad = :tipoActividad\n";
            params.put("tipoActividad", tipoActividad.toUpperCase());
        }

        String sql = """
                SELECT s.id,
                       s.nombre,
                       s.fecha_inicio,
                       s.tipo_actividad::text,
                       m.nombre   AS mountain_nombre,
                       r.nombre   AS ruta_nombre,
                       s.estado::text,
                       COUNT(sp.id) FILTER (
                           WHERE sp.estado_inscripcion::text != 'CANCELADO') AS total_participantes,
                       MAX(i.se_realizo::int)::boolean                        AS se_realizo
                FROM salida s
                LEFT JOIN rutas r   ON s.ruta_id      = r.id
                LEFT JOIN mountains m ON r.mountain_id = m.id
                LEFT JOIN salida_participantes sp ON sp.salida_id = s.id
                LEFT JOIN informe_salida i ON i.salida_id = s.id
                WHERE s.fecha_inicio BETWEEN :desde AND :hasta
                """
                + tipoClause
                + """
                GROUP BY s.id, s.nombre, s.fecha_inicio, s.tipo_actividad, m.nombre, r.nombre, s.estado
                ORDER BY s.fecha_inicio DESC
                LIMIT 500
                """;

        return jdbcClient.sql(sql)
                .params(params)
                .query((rs, row) -> new SalidaPeriodoItem(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getString("nombre"),
                        rs.getObject("fecha_inicio", LocalDate.class),
                        rs.getString("tipo_actividad"),
                        rs.getString("mountain_nombre"),
                        rs.getString("ruta_nombre"),
                        rs.getString("estado"),
                        rs.getInt("total_participantes"),
                        rs.getObject("se_realizo") != null
                                ? rs.getBoolean("se_realizo") : null))
                .list();
    }

    @PreAuthorize("isAuthenticated()")
    public List<MontanaPeriodoItem> buscarMontanasEnPeriodo(
            LocalDate fechaDesde, LocalDate fechaHasta, String tipoActividad) {

        var params = new java.util.HashMap<String, Object>();
        params.put("desde", fechaDesde);
        params.put("hasta", fechaHasta);

        String tipoClause = "";
        if (tipoActividad != null && !tipoActividad.isBlank()) {
            tipoClause = "  AND s.tipo_actividad = :tipoActividad\n";
            params.put("tipoActividad", tipoActividad.toUpperCase());
        }

        String sql = """
                SELECT m.id,
                       m.nombre,
                       m.region,
                       m.altitud,
                       COUNT(DISTINCT s.id)                                                    AS total_salidas,
                       COUNT(DISTINCT sp.socio_id) FILTER (
                           WHERE sp.estado_inscripcion::text != 'CANCELADO')                  AS total_participantes,
                       MIN(s.fecha_inicio)                                                     AS primera_fecha,
                       MAX(s.fecha_inicio)                                                     AS ultima_fecha
                FROM mountains m
                JOIN rutas r   ON r.mountain_id = m.id
                JOIN salida s  ON s.ruta_id     = r.id
                LEFT JOIN salida_participantes sp ON sp.salida_id = s.id
                WHERE s.fecha_inicio BETWEEN :desde AND :hasta
                """
                + tipoClause
                + """
                GROUP BY m.id, m.nombre, m.region, m.altitud
                ORDER BY total_salidas DESC, m.nombre ASC
                LIMIT 200
                """;

        return jdbcClient.sql(sql)
                .params(params)
                .query((rs, row) -> new MontanaPeriodoItem(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getString("region"),
                        rs.getInt("altitud"),
                        rs.getInt("total_salidas"),
                        rs.getInt("total_participantes"),
                        rs.getObject("primera_fecha", LocalDate.class),
                        rs.getObject("ultima_fecha",  LocalDate.class)))
                .list();
    }

    @PreAuthorize("isAuthenticated()")
    public List<RutaPeriodoItem> buscarRutasEnPeriodo(
            LocalDate fechaDesde, LocalDate fechaHasta, String tipoActividad) {

        var params = new java.util.HashMap<String, Object>();
        params.put("desde", fechaDesde);
        params.put("hasta", fechaHasta);

        String tipoClause = "";
        if (tipoActividad != null && !tipoActividad.isBlank()) {
            tipoClause = "  AND s.tipo_actividad = :tipoActividad\n";
            params.put("tipoActividad", tipoActividad.toUpperCase());
        }

        String sql = """
                SELECT r.id,
                       r.nombre,
                       r.tipo_actividad::text,
                       m.nombre   AS mountain_nombre,
                       COUNT(DISTINCT s.id)                                                    AS total_salidas,
                       COUNT(DISTINCT sp.socio_id) FILTER (
                           WHERE sp.estado_inscripcion::text != 'CANCELADO')                  AS total_participantes,
                       MIN(s.fecha_inicio)                                                     AS primera_fecha,
                       MAX(s.fecha_inicio)                                                     AS ultima_fecha
                FROM rutas r
                LEFT JOIN mountains m ON r.mountain_id = m.id
                JOIN salida s ON s.ruta_id = r.id
                LEFT JOIN salida_participantes sp ON sp.salida_id = s.id
                WHERE s.fecha_inicio BETWEEN :desde AND :hasta
                """
                + tipoClause
                + """
                GROUP BY r.id, r.nombre, r.tipo_actividad, m.nombre
                ORDER BY total_salidas DESC, r.nombre ASC
                LIMIT 200
                """;

        return jdbcClient.sql(sql)
                .params(params)
                .query((rs, row) -> new RutaPeriodoItem(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getString("tipo_actividad"),
                        rs.getString("mountain_nombre"),
                        rs.getInt("total_salidas"),
                        rs.getInt("total_participantes"),
                        rs.getObject("primera_fecha", LocalDate.class),
                        rs.getObject("ultima_fecha",  LocalDate.class)))
                .list();
    }

    // =========================================================================

    private boolean tieneRolPrivilegiado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_DIRECTIVO") || a.equals("ROLE_SECRETARIA"));
    }
}
