package com.sadday.app.socios.repository;

import com.sadday.app.socios.entity.EstadoHabilitacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoHabilitacionRepository extends JpaRepository<EstadoHabilitacion, Short> {
    Optional<EstadoHabilitacion> findByNombre(String nombre);
}
