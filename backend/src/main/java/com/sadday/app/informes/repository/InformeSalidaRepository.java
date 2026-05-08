package com.sadday.app.informes.repository;

import com.sadday.app.informes.entity.InformeSalida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface InformeSalidaRepository extends JpaRepository<InformeSalida, UUID> {

    Optional<InformeSalida> findBySalidaId(UUID salidaId);

    boolean existsBySalidaId(UUID salidaId);

    /** Carga en batch los informes de varias salidas. Para estadísticas de montañas. */
    List<InformeSalida> findBySalidaIdIn(List<UUID> salidaIds);

    /** Todos los informes de salidas que usan una ruta dada. Para el Planificador. */
    @Query("SELECT DISTINCT i FROM InformeSalida i LEFT JOIN FETCH i.segmentos WHERE i.salida.ruta.id = :rutaId")
    List<InformeSalida> findBySalidaRutaId(@Param("rutaId") Integer rutaId);

    /** IDs de salidas que ya tienen informe, para mostrar estado en el listado. */
    @Query("SELECT i.salida.id FROM InformeSalida i WHERE i.salida.id IN :salidaIds")
    Set<UUID> findSalidaIdsWithInforme(@Param("salidaIds") List<UUID> salidaIds);

    /**
     * Salidas donde el socio es Jefe de Salida, el estado es REALIZADA,
     * han pasado al menos 24 horas desde fecha_fin, y aún no existe informe.
     * Devuelve [id::text, nombre, fecha_fin].
     */
    @Query(value = """
        SELECT s.id::text, s.nombre, s.fecha_fin
        FROM salida s
        JOIN salida_participantes sp ON sp.salida_id = s.id
        JOIN salida_participante_dignidades spd ON spd.participante_id = sp.id
        JOIN dignidades d ON d.id = spd.dignidad_id
        WHERE sp.socio_id = CAST(:socioId AS uuid)
          AND d.nombre = 'Jefe de Salida'
          AND s.estado = 'REALIZADA'
          AND s.fecha_fin < CURRENT_DATE - INTERVAL '1 day'
          AND NOT EXISTS (SELECT 1 FROM informe_salida i WHERE i.salida_id = s.id)
        ORDER BY s.fecha_fin ASC
        """, nativeQuery = true)
    List<Object[]> findSalidasPendientesJefe(@Param("socioId") String socioId);
}
