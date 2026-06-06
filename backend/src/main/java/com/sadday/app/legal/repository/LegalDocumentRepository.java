package com.sadday.app.legal.repository;

import com.sadday.app.legal.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    Optional<LegalDocument> findByCodeAndActiveTrue(String code);

    List<LegalDocument> findByRequiredStageAndActiveTrue(String requiredStage);

    List<LegalDocument> findByActiveTrue();

    List<LegalDocument> findByCodeOrderByVersionDesc(String code);

    List<LegalDocument> findAllByOrderByCodeAscVersionDesc();

    Optional<LegalDocument> findByCodeAndVersion(String code, Integer version);

    boolean existsByCodeAndVersion(String code, Integer version);

    @Query("SELECT MAX(d.version) FROM LegalDocument d WHERE d.code = :code")
    Optional<Integer> findMaxVersionByCode(@Param("code") String code);

    /** Desactiva todas las versiones del mismo code excepto la indicada. */
    @Modifying
    @Query("UPDATE LegalDocument d SET d.active = false WHERE d.code = :code AND d.id <> :excludeId")
    void deactivateOtherVersions(@Param("code") String code, @Param("excludeId") UUID excludeId);

    @Query("SELECT d FROM LegalDocument d WHERE d.active = true AND d.required = true")
    List<LegalDocument> findActiveRequired();
}
