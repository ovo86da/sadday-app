package com.sadday.app.emergencycontacts.repository;

import com.sadday.app.emergencycontacts.entity.SocioEmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SocioEmergencyContactRepository extends JpaRepository<SocioEmergencyContact, UUID> {

    List<SocioEmergencyContact> findBySocioIdOrderByOrden(UUID socioId);

    void deleteBySocioId(UUID socioId);

    long countBySocioId(UUID socioId);
}
