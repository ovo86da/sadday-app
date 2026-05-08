package com.sadday.app.socios.repository;

import com.sadday.app.socios.entity.Socio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocioRepository extends JpaRepository<Socio, UUID>, JpaSpecificationExecutor<Socio> {

    long countByRolSistemaNombreAndEstadoAccesoCodigo(String rolNombre, String estadoAccesoCodigo);

    boolean existsByCedula(String cedula);

    boolean existsByCorreo(String correo);

    boolean existsByCedulaAndIdNot(String cedula, UUID id);

    boolean existsByCorreoAndIdNot(String correo, UUID id);

    Optional<Socio> findByCorreo(String correo);

    Optional<Socio> findByCedula(String cedula);

    /**
     * Búsqueda de socios por nombre y/o apellido (case-insensitive, coincidencia parcial).
     * Se usa para resolver nombres del .md al importar actas.
     */
    @Query("""
            SELECT s FROM Socio s
            WHERE (:nombre IS NULL OR LOWER(s.nombre)   LIKE LOWER(CONCAT('%', :nombre,   '%')))
              AND (:apellido IS NULL OR LOWER(s.apellido) LIKE LOWER(CONCAT('%', :apellido, '%')))
            ORDER BY s.apellido, s.nombre
            """)
    List<Socio> buscarPorNombreYApellido(@Param("nombre") String nombre,
                                         @Param("apellido") String apellido);

    /**
     * Socios con cumpleaños hoy (mismo mes y día), excluyendo Ex-socio y Pendiente Registro.
     * Usa EXTRACT de PostgreSQL via native query.
     */
    @Query(value = """
            SELECT s.* FROM socios s
            JOIN estado_acceso ea ON s.estado_acceso_id = ea.id
            WHERE EXTRACT(MONTH FROM s.fecha_nacimiento) = :mes
              AND EXTRACT(DAY   FROM s.fecha_nacimiento) = :dia
              AND ea.codigo NOT IN ('EX_MEMBER', 'PENDING_REGISTER')
            ORDER BY s.nombre, s.apellido
            """, nativeQuery = true)
    List<Socio> findCumpleanosHoy(@Param("mes") int mes, @Param("dia") int dia);

    /**
     * Socios con tipo 'Juvenil' que ya tienen 18 o más años.
     * El scheduler diario los transiciona a 'Socio Activo'.
     */
    @Query(value = """
            SELECT s.* FROM socios s
            JOIN tipo_socio_club t ON s.tipo_socio_id = t.id
            WHERE t.nombre = 'Juvenil'
              AND EXTRACT(YEAR FROM AGE(s.fecha_nacimiento)) >= 18
            """, nativeQuery = true)
    List<Socio> findJuvenilesMayoresDeEdad();
}
