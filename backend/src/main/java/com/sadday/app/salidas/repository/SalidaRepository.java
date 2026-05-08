package com.sadday.app.salidas.repository;

import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.salidas.entity.EstadoSalida;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalidaRepository extends JpaRepository<Salida, UUID>, JpaSpecificationExecutor<Salida> {

    /** Todas las salidas que usan rutas de una montaña específica. Para estadísticas. */
    List<Salida> findByRuta_MountainId(int mountainId);

    /** Verifica si existe alguna salida (no eliminada) para una ruta dada. */
    boolean existsByRutaIdAndEliminadaFalse(Integer rutaId);

    /**
     * Carga la salida con bloqueo pesimista de escritura.
     * Usado en {@code inscribir} para serializar el chequeo de capacidad.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Salida s WHERE s.id = :id")
    Optional<Salida> findByIdWithLock(@Param("id") UUID id);

    /**
     * Salidas PLANIFICADA cuya fecha de inicio ya llegó pero aún no ha terminado.
     * El scheduler las transiciona a EN_CURSO.
     */
    @Query("SELECT s FROM Salida s WHERE s.estado = :planificada AND s.eliminada = false AND s.fechaInicio <= :hoy AND s.fechaFin >= :hoy")
    List<Salida> findSalidasParaIniciar(
            @Param("planificada") EstadoSalida planificada,
            @Param("hoy") LocalDate hoy);

    /**
     * Salidas PLANIFICADA o EN_CURSO cuya fecha de fin ya pasó.
     * El scheduler las transiciona a REALIZADA.
     */
    @Query("SELECT s FROM Salida s WHERE s.estado IN :estados AND s.eliminada = false AND s.fechaFin < :hoy")
    List<Salida> findSalidasParaFinalizar(
            @Param("estados") List<EstadoSalida> estados,
            @Param("hoy") LocalDate hoy);

    /**
     * Salidas activas (PLANIFICADA o EN_CURSO) cuyas fechas se solapan con el rango dado.
     * Condición de solapamiento: fechaInicio <= paramFechaFin AND fechaFin >= paramFechaInicio.
     * Se excluye opcionalmente la salida en edición ({@code excludeId}).
     */
    @Query("SELECT s FROM Salida s " +
           "WHERE s.estado IN :estados " +
           "AND s.eliminada = false " +
           "AND s.fechaInicio <= :fechaFin " +
           "AND s.fechaFin >= :fechaInicio " +
           "AND (:excludeId IS NULL OR s.id <> :excludeId)")
    List<Salida> findSolapadas(
            @Param("estados") List<EstadoSalida> estados,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin,
            @Param("excludeId") UUID excludeId);

    List<Salida> findByJefeAbandonoNombreIsNotNull();
}
