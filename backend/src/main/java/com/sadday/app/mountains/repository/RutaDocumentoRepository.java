package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.RutaDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RutaDocumentoRepository extends JpaRepository<RutaDocumento, UUID> {

    List<RutaDocumento> findByRutaIdOrderByCreatedAtAsc(Integer rutaId);

    Optional<RutaDocumento> findByIdAndRutaId(UUID id, Integer rutaId);
}
