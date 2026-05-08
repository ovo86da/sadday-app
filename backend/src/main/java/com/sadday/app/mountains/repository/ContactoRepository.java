package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.Contacto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactoRepository extends JpaRepository<Contacto, Integer> {

    Optional<Contacto> findByTelefono(String telefono);

    @Query("""
        SELECT c FROM Contacto c
        WHERE (CAST(:q AS string) IS NULL OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                                         OR c.telefono LIKE CONCAT('%', CAST(:q AS string), '%'))
    """)
    Page<Contacto> buscar(@Param("q") String q, Pageable pageable);

    @Query("""
        SELECT c FROM Contacto c
        WHERE (CAST(:q AS string) IS NULL OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                                         OR c.telefono LIKE CONCAT('%', CAST(:q AS string), '%'))
        ORDER BY c.nombre ASC
    """)
    List<Contacto> buscarSugerencias(@Param("q") String q, Pageable pageable);
}
