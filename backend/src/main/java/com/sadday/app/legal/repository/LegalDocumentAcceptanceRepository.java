package com.sadday.app.legal.repository;

import com.sadday.app.legal.entity.LegalDocumentAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LegalDocumentAcceptanceRepository extends JpaRepository<LegalDocumentAcceptance, UUID> {

    List<LegalDocumentAcceptance> findBySocioIdOrderByAcceptedAtDesc(UUID socioId);

    List<LegalDocumentAcceptance> findByLegalDocumentIdOrderByAcceptedAtDesc(UUID legalDocumentId);

    boolean existsBySocioIdAndLegalDocumentId(UUID socioId, UUID legalDocumentId);

    @Query("SELECT COUNT(a) > 0 FROM LegalDocumentAcceptance a " +
           "WHERE a.socio.id = :socioId AND a.documentCode = :code " +
           "AND a.documentVersion = :version AND a.accepted = true")
    boolean hasValidAcceptance(
            @Param("socioId") UUID socioId,
            @Param("code") String code,
            @Param("version") Integer version);

    /** Verdadero si el socio tiene al menos una aceptación válida del código de documento indicado. */
    @Query("SELECT COUNT(a) > 0 FROM LegalDocumentAcceptance a " +
           "WHERE a.socio.id = :socioId AND a.documentCode = :code AND a.accepted = true")
    boolean hasAnyAcceptance(@Param("socioId") UUID socioId, @Param("code") String code);

    /** Versión más alta aceptada por el socio para el código indicado. */
    @Query("SELECT MAX(a.documentVersion) FROM LegalDocumentAcceptance a " +
           "WHERE a.socio.id = :socioId AND a.documentCode = :code AND a.accepted = true")
    Optional<Integer> findLatestAcceptedVersion(@Param("socioId") UUID socioId, @Param("code") String code);

    /** IDs de socios que YA aceptaron el documento indicado. */
    @Query("SELECT DISTINCT a.socio.id FROM LegalDocumentAcceptance a " +
           "WHERE a.legalDocument.id = :documentId AND a.accepted = true")
    Set<UUID> findSocioIdsWithAcceptance(@Param("documentId") UUID documentId);
}
