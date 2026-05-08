package com.sadday.app.actas.repository;

import com.sadday.app.actas.entity.ActaInformeSalida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActaInformeSalidaRepository extends JpaRepository<ActaInformeSalida, Long> {

    List<ActaInformeSalida> findByActaId(UUID actaId);

    boolean existsByActaIdAndInformeId(UUID actaId, UUID informeId);
}
