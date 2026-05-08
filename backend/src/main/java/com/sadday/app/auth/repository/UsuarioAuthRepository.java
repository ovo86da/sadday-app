package com.sadday.app.auth.repository;

import com.sadday.app.auth.dto.SocioAuthView;
import com.sadday.app.auth.entity.UsuarioAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UsuarioAuthRepository extends JpaRepository<UsuarioAuth, UUID> {

    Optional<UsuarioAuth> findByUsername(String username);

    Optional<UsuarioAuth> findBySocioId(UUID socioId);

    boolean existsByUsername(String username);

    boolean existsBySocioId(UUID socioId);

    @Query("SELECT u.socioId FROM UsuarioAuth u WHERE u.socioId IN :ids")
    Set<UUID> findSocioIdsWithAccount(@Param("ids") Collection<UUID> ids);

    /**
     * Proyección nativa que devuelve los datos mínimos de un socio necesarios
     * para generar el JWT: nombre, apellido, nombre del rol y estado de habilitación.
     */
    @Query(nativeQuery = true, value = """
            SELECT s.nombre,
                   s.apellido,
                   s.correo,
                   r.nombre           AS rol_nombre,
                   eh.nombre          AS estado_habilitacion,
                   cs.nombre          AS nivel_tecnico,
                   ea.codigo          AS estado_acceso,
                   s.es_jefe_montana  AS es_jefe_montana
            FROM   socios s
            JOIN   roles_sistema       r  ON s.rol_sistema_id         = r.id
            JOIN   estado_habilitacion eh ON s.estado_habilitacion_id = eh.id
            JOIN   estado_acceso       ea ON s.estado_acceso_id       = ea.id
            LEFT JOIN clasificacion_socio cs ON s.nivel_tecnico_id   = cs.id
            WHERE  s.id = :socioId
            """)
    Optional<SocioAuthView> findSocioAuthView(@Param("socioId") UUID socioId);
}
