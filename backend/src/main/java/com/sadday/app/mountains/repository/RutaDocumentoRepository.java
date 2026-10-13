package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.RutaDocumento;
import com.sadday.app.mountains.entity.TipoDocumentoRuta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RutaDocumentoRepository extends JpaRepository<RutaDocumento, UUID> {

    List<RutaDocumento> findByRutaIdOrderByCreatedAtAsc(Integer rutaId);

    Optional<RutaDocumento> findByIdAndRutaId(UUID id, Integer rutaId);

    /**
     * Documentos de un tipo que un socio ya subió a una ruta.
     *
     * <p>Devuelve lista y no {@code Optional} a propósito: la regla de «un
     * track por persona» se aplica al subir, pero si por lo que sea hubiera
     * más de uno, reemplazar debe limpiarlos todos en vez de fallar.
     */
    List<RutaDocumento> findByRutaIdAndTipoAndSubidoPorId(
            Integer rutaId, TipoDocumentoRuta tipo, UUID subidoPorId);
}
