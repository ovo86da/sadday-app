package com.sadday.app.socios.repository;

import com.sadday.app.socios.entity.RolSistema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolSistemaRepository extends JpaRepository<RolSistema, Short> {
    Optional<RolSistema> findByNombre(String nombre);
}
