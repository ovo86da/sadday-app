package com.sadday.app.actas.repository;

import com.sadday.app.actas.entity.AsistenteReunion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsistenteReunionRepository extends JpaRepository<AsistenteReunion, Long> {

    List<AsistenteReunion> findByActaId(UUID actaId);

    int countByActaId(UUID actaId);

    boolean existsByActaIdAndSocioId(UUID actaId, UUID socioId);

    Optional<AsistenteReunion> findByActaIdAndSocioId(UUID actaId, UUID socioId);
}
