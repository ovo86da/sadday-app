package com.sadday.app.socios.repository;

import com.sadday.app.socios.entity.EstadoCuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EstadoCuotaRepository extends JpaRepository<EstadoCuota, Long> {

    List<EstadoCuota> findBySocioIdOrderByFechaDesc(UUID socioId);
}
