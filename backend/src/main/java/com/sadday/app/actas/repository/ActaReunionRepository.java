package com.sadday.app.actas.repository;

import com.sadday.app.actas.entity.ActaReunion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ActaReunionRepository extends JpaRepository<ActaReunion, UUID> {

    /**
     * Lista actas con filtro de tipo y búsqueda Full Text Search opcional.
     *
     * <p>Cuando {@code tipoActa} es null se devuelven todos los tipos (Admin/Secretaria/Directivo).
     * Cuando {@code q} es null se devuelven todas las actas del tipo indicado ordenadas por fecha desc.
     * Cuando {@code q} tiene valor, filtra por {@code search_vector} usando {@code plainto_tsquery}.
     */
    @Query(
        value = """
            SELECT a.* FROM actas_reunion a
            WHERE (:tipoActa IS NULL OR a.tipo_acta = CAST(:tipoActa AS tipo_acta))
              AND (:q IS NULL OR a.search_vector @@ plainto_tsquery('spanish', :q))
            ORDER BY a.fecha DESC, a.created_at DESC
            """,
        countQuery = """
            SELECT COUNT(*) FROM actas_reunion a
            WHERE (:tipoActa IS NULL OR a.tipo_acta = CAST(:tipoActa AS tipo_acta))
              AND (:q IS NULL OR a.search_vector @@ plainto_tsquery('spanish', :q))
            """,
        nativeQuery = true
    )
    Page<ActaReunion> buscarConFts(
            @Param("tipoActa") String tipoActa,
            @Param("q") String q,
            Pageable pageable);
}
