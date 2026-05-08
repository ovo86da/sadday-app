package com.sadday.app.socios.repository;

import com.sadday.app.socios.entity.EstadoAcceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoAccesoRepository extends JpaRepository<EstadoAcceso, Short> {
    Optional<EstadoAcceso> findByCodigo(String codigo);
}
