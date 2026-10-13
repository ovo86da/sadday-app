package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.RutaDocumentoConfirmacion;
import com.sadday.app.mountains.entity.RutaDocumentoConfirmacionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RutaDocumentoConfirmacionRepository
        extends JpaRepository<RutaDocumentoConfirmacion, RutaDocumentoConfirmacionId> {

    /**
     * Cuántas confirmaciones tiene cada documento de una ruta.
     *
     * <p>Una sola consulta para toda la lista, en lugar de una por documento:
     * la ficha de una ruta puede traer bastantes ficheros.
     */
    @Query("""
           SELECT c.id.rutaDocumentoId, COUNT(c)
             FROM RutaDocumentoConfirmacion c
            WHERE c.rutaDocumento.ruta.id = :rutaId
            GROUP BY c.id.rutaDocumentoId
           """)
    List<Object[]> contarPorRuta(Integer rutaId);

    /** Documentos de esta ruta que el socio dado ya confirmó. */
    @Query("""
           SELECT c.id.rutaDocumentoId
             FROM RutaDocumentoConfirmacion c
            WHERE c.rutaDocumento.ruta.id = :rutaId
              AND c.id.socioId = :socioId
           """)
    List<UUID> confirmadosPorSocioEnRuta(Integer rutaId, UUID socioId);

    long countByIdRutaDocumentoId(UUID rutaDocumentoId);
}
