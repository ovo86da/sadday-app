package com.sadday.app.socios.repository;

import com.sadday.app.socios.entity.ClasificacionSocio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClasificacionSocioRepository extends JpaRepository<ClasificacionSocio, String> {
    Optional<ClasificacionSocio> findByNombreIgnoreCase(String nombre);
}
