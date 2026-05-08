package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.ContactoRuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContactoRutaRepository extends JpaRepository<ContactoRuta, Integer> {
    List<ContactoRuta> findByRutaIdAndActivoTrue(Integer rutaId);

    @Query("""
        SELECT cr FROM ContactoRuta cr
        JOIN FETCH cr.contacto
        WHERE cr.ruta.id = :rutaId AND cr.activo = true
    """)
    List<ContactoRuta> findActivosByRutaId(@Param("rutaId") Integer rutaId);

    @Query("""
        SELECT DISTINCT cr.tipoContacto FROM ContactoRuta cr
        WHERE cr.contacto.id = :contactoId AND cr.activo = true AND cr.tipoContacto IS NOT NULL
    """)
    List<String> findTiposActivosByContactoId(@Param("contactoId") Integer contactoId);
}
