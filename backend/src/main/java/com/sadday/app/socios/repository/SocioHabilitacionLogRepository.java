package com.sadday.app.socios.repository;

import com.sadday.app.socios.entity.SocioHabilitacionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SocioHabilitacionLogRepository extends JpaRepository<SocioHabilitacionLog, Long> {

    List<SocioHabilitacionLog> findBySocioIdOrderByCambiadoEnDesc(UUID socioId);
}
