package com.sadday.app.medicalinfo.repository;

import com.sadday.app.medicalinfo.entity.SocioMedicalInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocioMedicalInfoRepository extends JpaRepository<SocioMedicalInfo, UUID> {

    Optional<SocioMedicalInfo> findBySocioId(UUID socioId);

    boolean existsBySocioId(UUID socioId);

    void deleteBySocioId(UUID socioId);
}
