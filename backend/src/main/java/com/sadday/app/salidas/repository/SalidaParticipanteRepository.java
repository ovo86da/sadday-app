package com.sadday.app.salidas.repository;

import com.sadday.app.salidas.entity.EstadoInscripcion;
import com.sadday.app.salidas.entity.SalidaParticipante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface SalidaParticipanteRepository extends JpaRepository<SalidaParticipante, Long> {

    List<SalidaParticipante> findBySalidaId(UUID salidaId);

    Optional<SalidaParticipante> findBySalidaIdAndSocioId(UUID salidaId, UUID socioId);

    boolean existsBySalidaIdAndSocioId(UUID salidaId, UUID socioId);

    /** Cuenta inscripciones activas (INSCRITO + CONFIRMADO) para verificar capacidad. */
    int countBySalidaIdAndEstadoInscripcionIn(UUID salidaId, List<EstadoInscripcion> estados);

    /** Todas las participaciones de un socio que no estén canceladas (para historial/estadísticas). */
    List<SalidaParticipante> findBySocioIdAndEstadoInscripcionNot(UUID socioId, EstadoInscripcion estado);

    /** Cuenta participaciones de un socio que no estén en un estado dado. */
    int countBySocioIdAndEstadoInscripcionNot(UUID socioId, EstadoInscripcion estado);

    /**
     * Igual que {@link #findBySocioIdAndEstadoInscripcionNot} pero con fetch join
     * de salida → ruta → mountain para evitar N+1 en el historial del socio.
     */
    @Query("SELECT p FROM SalidaParticipante p " +
           "JOIN FETCH p.salida s " +
           "JOIN FETCH s.ruta r " +
           "LEFT JOIN FETCH r.mountain " +
           "WHERE p.socio.id = :socioId AND p.estadoInscripcion <> :estado")
    List<SalidaParticipante> findBySocioIdAndEstadoInscripcionNotFetch(
            @Param("socioId") UUID socioId,
            @Param("estado") EstadoInscripcion estado);

    /**
     * Inscripciones PENDIENTE_APROBACION donde el Directivo/Admin aún no ha decidido.
     * Devueltas para que cualquier Directivo o Admin pueda decidir.
     */
    @Query("SELECT p FROM SalidaParticipante p " +
           "WHERE p.estadoInscripcion = :estado " +
           "AND p.riesgoAprobadoPorDirectivo IS NULL")
    List<SalidaParticipante> findPendientesParaDirectivo(@Param("estado") EstadoInscripcion estado);

    /**
     * Inscripciones PENDIENTE_APROBACION que aún no tienen aprobación del Jefe de Salida
     * y donde el usuario especificado es Jefe de Salida de esa salida.
     */
    @Query("SELECT p FROM SalidaParticipante p " +
           "WHERE p.estadoInscripcion = :estado " +
           "AND p.riesgoAprobadoPorJefe IS NULL " +
           "AND EXISTS (" +
           "  SELECT 1 FROM SalidaParticipanteDignidad d " +
           "  WHERE d.participante.salida.id = p.salida.id " +
           "  AND d.participante.socio.id = :jefeId " +
           "  AND d.dignidad.nombre = 'Jefe de Salida'" +
           ")")
    List<SalidaParticipante> findPendientesParaJefe(
            @Param("jefeId") UUID jefeId,
            @Param("estado") EstadoInscripcion estado);
}
