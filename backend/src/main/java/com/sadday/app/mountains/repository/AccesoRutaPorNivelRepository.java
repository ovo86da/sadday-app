package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.AccesoRutaPorNivel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccesoRutaPorNivelRepository extends JpaRepository<AccesoRutaPorNivel, Short> {
    Optional<AccesoRutaPorNivel> findByNivelSocioId(String nivelSocioId);
    List<AccesoRutaPorNivel> findAllByOrderByNivelSocioNivelAsc();
}
