package com.sadday.app.activityrisk.repository;

import com.sadday.app.activityrisk.entity.ActivityRiskAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActivityRiskAcceptanceRepository extends JpaRepository<ActivityRiskAcceptance, UUID> {

    boolean existsBySocioIdAndActivityIdAndRiskDocumentId(UUID socioId, UUID activityId, UUID riskDocumentId);

    boolean existsBySocioIdAndActivityId(UUID socioId, UUID activityId);
}
