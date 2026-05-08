package com.sadday.app.informes.repository;

import com.sadday.app.informes.entity.InformeSalidaReconocimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InformeSalidaReconocimientoRepository extends JpaRepository<InformeSalidaReconocimiento, Long> {

    List<InformeSalidaReconocimiento> findByInformeId(UUID informeId);
}
