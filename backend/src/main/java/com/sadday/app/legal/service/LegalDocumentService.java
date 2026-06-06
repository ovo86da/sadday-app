package com.sadday.app.legal.service;

import com.sadday.app.legal.dto.*;
import com.sadday.app.legal.entity.LegalDocument;
import com.sadday.app.legal.repository.LegalDocumentAcceptanceRepository;
import com.sadday.app.legal.repository.LegalDocumentRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LegalDocumentService {

    private final LegalDocumentRepository         legalDocumentRepository;
    private final LegalDocumentAcceptanceRepository acceptanceRepository;
    private final SocioRepository                 socioRepository;

    // -------------------------------------------------------------------------
    // Consultas públicas (pre-auth o autenticado)
    // -------------------------------------------------------------------------

    /** Documentos activos, opcionalmente filtrados por etapa. */
    @Transactional(readOnly = true)
    public List<LegalDocumentResponse> getActiveDocuments(String requiredStage) {
        List<LegalDocument> docs = requiredStage == null
                ? legalDocumentRepository.findByActiveTrue()
                : legalDocumentRepository.findByRequiredStageAndActiveTrue(requiredStage);
        return docs.stream().map(this::toResponse).toList();
    }

    /** Documento activo por código. Lanza excepción si no existe o no está activo. */
    @Transactional(readOnly = true)
    public LegalDocumentResponse getActiveDocument(String code) {
        return legalDocumentRepository.findByCodeAndActiveTrue(code)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
    }

    /** Documento por ID. */
    @Transactional(readOnly = true)
    public LegalDocumentResponse getDocument(UUID id) {
        return legalDocumentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
    }

    // -------------------------------------------------------------------------
    // Administración
    // -------------------------------------------------------------------------

    /** Lista todos los documentos con todas sus versiones para el panel admin. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public List<LegalDocumentSummaryResponse> getAllDocumentsAdmin() {
        return legalDocumentRepository.findAllByOrderByCodeAscVersionDesc()
                .stream().map(this::toSummaryResponse).toList();
    }

    /** Crea un documento nuevo (versión 1). */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public LegalDocumentResponse createDocument(CreateLegalDocumentRequest req, UUID actorId) {
        if (legalDocumentRepository.existsByCodeAndVersion(req.code(), 1)) {
            throw new BusinessException(ErrorCode.LEGAL_DOCUMENT_VERSION_EXISTS,
                    "Ya existe la versión 1 de un documento con el código '" + req.code() + "'");
        }
        Socio actor = socioRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        LegalDocument doc = LegalDocument.builder()
                .code(req.code().toUpperCase())
                .title(req.title())
                .description(req.description())
                .documentType(req.documentType())
                .requiredStage(req.requiredStage())
                .version(1)
                .content(req.content())
                .contentHash(sha256(req.content()))
                .active(false)
                .required(req.required())
                .requiresReacceptanceOnNewVersion(req.requiresReacceptanceOnNewVersion())
                .approvedBy(actor)
                .build();

        return toResponse(legalDocumentRepository.save(doc));
    }

    /**
     * Crea una nueva versión de un documento existente.
     * El frontend envía solo el contenido; el backend calcula el hash.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public LegalDocumentResponse createNewVersion(UUID existingDocumentId, NewVersionRequest req, UUID actorId) {
        LegalDocument existing = legalDocumentRepository.findById(existingDocumentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
        Socio actor = socioRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        int newVersion = legalDocumentRepository.findMaxVersionByCode(existing.getCode())
                .orElse(0) + 1;

        LegalDocument newDoc = LegalDocument.builder()
                .code(existing.getCode())
                .title(existing.getTitle())
                .description(existing.getDescription())
                .documentType(existing.getDocumentType())
                .requiredStage(existing.getRequiredStage())
                .version(newVersion)
                .content(req.content())
                .contentHash(sha256(req.content()))
                .active(false)
                .required(existing.isRequired())
                .requiresReacceptanceOnNewVersion(existing.isRequiresReacceptanceOnNewVersion())
                .approvedBy(actor)
                .build();

        return toResponse(legalDocumentRepository.save(newDoc));
    }

    /**
     * Activa una versión de un documento y desactiva las anteriores.
     * Solo ADMIN puede activar.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public LegalDocumentResponse activateVersion(UUID documentId, UUID actorId) {
        LegalDocument doc = legalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND));

        legalDocumentRepository.deactivateOtherVersions(doc.getCode(), doc.getId());

        doc.setActive(true);
        doc.setApprovedAt(LocalDateTime.now());
        doc.setApprovedBy(socioRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND)));

        return toResponse(legalDocumentRepository.save(doc));
    }

    /** Aceptaciones de un documento específico (admin). */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public List<LegalDocumentAcceptanceAdminResponse> getAcceptancesByDocument(UUID documentId) {
        if (!legalDocumentRepository.existsById(documentId)) {
            throw new BusinessException(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND);
        }
        return acceptanceRepository.findByLegalDocumentIdOrderByAcceptedAtDesc(documentId)
                .stream().map(a -> new LegalDocumentAcceptanceAdminResponse(
                        a.getId(),
                        a.getSocio().getId(),
                        a.getSocio().getNombre() + " " + a.getSocio().getApellido(),
                        a.getSocio().getCedula(),
                        a.getDocumentCode(),
                        a.getDocumentVersion(),
                        a.getAcceptedAt(),
                        a.getIpAddress()
                )).toList();
    }

    /**
     * Por cada documento activo y requerido, lista los socios activos que
     * aún no lo han aceptado.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public List<PendingAcceptancesResponse> getPendingAcceptances() {
        return legalDocumentRepository.findActiveRequired().stream()
                .map(doc -> {
                    Set<UUID> accepted = acceptanceRepository.findSocioIdsWithAcceptance(doc.getId());
                    List<Socio> pending = accepted.isEmpty()
                            ? socioRepository.findAllActive()
                            : socioRepository.findActiveSociosNotIn(accepted);
                    List<PendingAcceptancesResponse.SocioPendienteDto> dtos = pending.stream()
                            .map(s -> new PendingAcceptancesResponse.SocioPendienteDto(
                                    s.getId(), s.getNombre(), s.getApellido(),
                                    s.getCedula(), s.getCorreo()))
                            .toList();
                    return new PendingAcceptancesResponse(
                            doc.getId(), doc.getCode(), doc.getTitle(), doc.getVersion(), dtos);
                }).toList();
    }

    /** Estado legal de un socio: qué documentos aceptó y cuáles le faltan. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public SocioLegalStatusResponse getSocioLegalStatus(UUID socioId) {
        if (!socioRepository.existsById(socioId)) {
            throw new BusinessException(ErrorCode.SOCIO_NOT_FOUND);
        }
        List<LegalDocument> activeDocs = legalDocumentRepository.findActiveRequired();
        List<SocioLegalStatusResponse.DocumentStatusDto> statuses = activeDocs.stream()
                .map(doc -> {
                    boolean accepted = acceptanceRepository.hasValidAcceptance(
                            socioId, doc.getCode(), doc.getVersion());
                    Integer acceptedVersion = accepted ? doc.getVersion() : null;
                    return new SocioLegalStatusResponse.DocumentStatusDto(
                            doc.getId(), doc.getCode(), doc.getTitle(), doc.getRequiredStage(),
                            accepted, acceptedVersion, doc.getVersion(),
                            !accepted && doc.isRequiresReacceptanceOnNewVersion());
                }).toList();

        boolean allAccepted = statuses.stream().allMatch(SocioLegalStatusResponse.DocumentStatusDto::accepted);
        return new SocioLegalStatusResponse(socioId, allAccepted, statuses);
    }

    /**
     * Socios bloqueados para actividades por documentos legales pendientes.
     * En Fase 5 este check se extenderá con información médica y contactos.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public List<PendingAcceptancesResponse.SocioPendienteDto> getSociosBlockedByDocuments() {
        List<LegalDocument> activeDocs = legalDocumentRepository.findActiveRequired();
        if (activeDocs.isEmpty()) return List.of();

        Set<UUID> blockedIds = new java.util.HashSet<>();
        for (LegalDocument doc : activeDocs) {
            Set<UUID> accepted = acceptanceRepository.findSocioIdsWithAcceptance(doc.getId());
            List<Socio> pending = accepted.isEmpty()
                    ? socioRepository.findAllActive()
                    : socioRepository.findActiveSociosNotIn(accepted);
            pending.forEach(s -> blockedIds.add(s.getId()));
        }

        return socioRepository.findAllById(blockedIds).stream()
                .map(s -> new PendingAcceptancesResponse.SocioPendienteDto(
                        s.getId(), s.getNombre(), s.getApellido(), s.getCedula(), s.getCorreo()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private LegalDocumentResponse toResponse(LegalDocument doc) {
        String approvedByName = doc.getApprovedBy() != null
                ? doc.getApprovedBy().getNombre() + " " + doc.getApprovedBy().getApellido()
                : null;
        return new LegalDocumentResponse(
                doc.getId(), doc.getCode(), doc.getTitle(), doc.getDescription(),
                doc.getDocumentType(), doc.getRequiredStage(), doc.getVersion(),
                doc.getContent(), doc.getContentHash(), doc.isActive(), doc.isRequired(),
                doc.isRequiresReacceptanceOnNewVersion(), doc.getApprovedAt(), approvedByName,
                doc.getCreatedAt(), doc.getUpdatedAt());
    }

    private LegalDocumentSummaryResponse toSummaryResponse(LegalDocument doc) {
        return new LegalDocumentSummaryResponse(
                doc.getId(), doc.getCode(), doc.getTitle(), doc.getDocumentType(),
                doc.getRequiredStage(), doc.getVersion(), doc.isActive(), doc.isRequired(),
                doc.getApprovedAt(), doc.getCreatedAt());
    }
}
