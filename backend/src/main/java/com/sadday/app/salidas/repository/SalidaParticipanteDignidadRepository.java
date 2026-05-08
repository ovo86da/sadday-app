package com.sadday.app.salidas.repository;

import com.sadday.app.salidas.entity.SalidaParticipanteDignidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SalidaParticipanteDignidadRepository extends JpaRepository<SalidaParticipanteDignidad, Long> {

    List<SalidaParticipanteDignidad> findByParticipanteId(Long participanteId);

    List<SalidaParticipanteDignidad> findByParticipanteIdIn(List<Long> participanteIds);

    boolean existsByParticipanteIdAndDignidadId(Long participanteId, Integer dignidadId);

    boolean existsByParticipanteIdAndDignidad_Nombre(Long participanteId, String dignidadNombre);

    /** Todas las dignidades asignadas a un socio (en todas sus salidas). Para estadísticas. */
    List<SalidaParticipanteDignidad> findByParticipante_SocioId(UUID socioId);

    /** Busca la asignación de una dignidad específica (por nombre) a cualquier participante de una salida. */
    List<SalidaParticipanteDignidad> findByParticipante_Salida_IdAndDignidad_Nombre(UUID salidaId, String dignidadNombre);

    void deleteByParticipanteIdAndDignidadId(Long participanteId, Integer dignidadId);

    /** Cuenta dignidades por salida, socio y nombre (>0 significa que existe). */
    @Query("SELECT COUNT(spd) FROM SalidaParticipanteDignidad spd " +
           "WHERE spd.participante.salida.id = :salidaId " +
           "AND spd.participante.socio.id = :socioId " +
           "AND spd.dignidad.nombre = :nombre")
    long countBySalidaSocioYDignidad(
            @Param("salidaId") UUID salidaId,
            @Param("socioId") UUID socioId,
            @Param("nombre") String nombre);
}
