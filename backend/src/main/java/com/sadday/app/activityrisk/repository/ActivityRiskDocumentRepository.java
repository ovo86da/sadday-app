package com.sadday.app.activityrisk.repository;

import com.sadday.app.activityrisk.entity.ActivityRiskDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ActivityRiskDocumentRepository extends JpaRepository<ActivityRiskDocument, UUID> {

    Optional<ActivityRiskDocument> findByActivityIdAndActiveTrue(UUID activityId);

    boolean existsByActivityIdAndActiveTrue(UUID activityId);

    @Query("SELECT MAX(d.version) FROM ActivityRiskDocument d WHERE d.activity.id = :activityId")
    Optional<Integer> findMaxVersionByActivityId(@Param("activityId") UUID activityId);

    @Modifying
    @Query("UPDATE ActivityRiskDocument d SET d.active = false WHERE d.activity.id = :activityId AND d.id <> :excludeId")
    void deactivateOtherVersions(@Param("activityId") UUID activityId, @Param("excludeId") UUID excludeId);
}
