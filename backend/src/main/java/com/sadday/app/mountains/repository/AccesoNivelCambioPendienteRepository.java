package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.AccesoNivelCambioPendiente;
import com.sadday.app.mountains.entity.AccesoNivelCambioPendiente.Estado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccesoNivelCambioPendienteRepository
        extends JpaRepository<AccesoNivelCambioPendiente, UUID> {

    Optional<AccesoNivelCambioPendiente> findByNivelSocioIdAndEstado(String nivelSocioId, Estado estado);

    /**
     * Todos los pendientes, del más viejo al más nuevo.
     *
     * <p>Del más viejo primero a propósito: los cambios no caducan, así que lo
     * que lleva más tiempo esperando es lo que más conviene que se vea.
     */
    @Query("""
           SELECT c FROM AccesoNivelCambioPendiente c
           JOIN FETCH c.nivelSocio
           JOIN FETCH c.solicitadoPor
           WHERE c.estado = :estado
           ORDER BY c.solicitadoEn ASC
           """)
    List<AccesoNivelCambioPendiente> findPorEstado(@Param("estado") Estado estado);

    default List<AccesoNivelCambioPendiente> findPendientes() {
        return findPorEstado(Estado.PENDIENTE);
    }
}
