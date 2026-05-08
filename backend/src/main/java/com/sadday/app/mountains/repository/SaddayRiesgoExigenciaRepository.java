package com.sadday.app.mountains.repository;

import com.sadday.app.mountains.entity.SaddayRiesgoExigencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaddayRiesgoExigenciaRepository extends JpaRepository<SaddayRiesgoExigencia, String> {
    List<SaddayRiesgoExigencia> findAllByOrderByRankAsc();
}
