package com.sadday.app.shared.repository;

import com.sadday.app.shared.entity.ConfiguracionSistema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracionSistemaRepository extends JpaRepository<ConfiguracionSistema, Short> {
    Optional<ConfiguracionSistema> findByClave(String clave);
}
