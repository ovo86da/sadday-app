package com.sadday.app.legal.service;

import com.sadday.app.legal.dto.LegalDocumentAcceptanceResponse;
import com.sadday.app.legal.entity.LegalDocument;
import com.sadday.app.legal.entity.LegalDocumentAcceptance;
import com.sadday.app.legal.repository.LegalDocumentAcceptanceRepository;
import com.sadday.app.legal.repository.LegalDocumentRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LegalDocumentAcceptanceService {

    private final LegalDocumentAcceptanceRepository acceptanceRepository;
    private final LegalDocumentRepository           legalDocumentRepository;
    private final SocioRepository                   socioRepository;

    /**
     * Registra la aceptación de un documento por parte de un socio.
     * IP y user-agent se capturan en el controller desde el HttpServletRequest
     * y se pasan como parámetros — nunca vienen del frontend.
     */
    @Transactional
    public LegalDocumentAcceptanceResponse acceptDocument(
            UUID socioId, UUID documentId, String ipAddress, String userAgent) {

        LegalDocument doc = legalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEGAL_DOCUMENT_NOT_FOUND));

        if (!doc.isActive()) {
            throw new BusinessException(ErrorCode.LEGAL_DOCUMENT_INACTIVE);
        }
        if (acceptanceRepository.existsBySocioIdAndLegalDocumentId(socioId, documentId)) {
            throw new BusinessException(ErrorCode.LEGAL_DOCUMENT_ALREADY_ACCEPTED);
        }

        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        LegalDocumentAcceptance acceptance = LegalDocumentAcceptance.builder()
                .socio(socio)
                .legalDocument(doc)
                .documentCode(doc.getCode())
                .documentVersion(doc.getVersion())
                .contentHash(doc.getContentHash())
                .ipAddress(truncate(ipAddress, 45))
                .userAgent(userAgent)
                .accepted(true)
                .build();

        LegalDocumentAcceptance saved = acceptanceRepository.save(acceptance);
        return toResponse(saved, doc.getTitle());
    }

    /** Historial de aceptaciones del socio autenticado. */
    @Transactional(readOnly = true)
    public List<LegalDocumentAcceptanceResponse> getMyAcceptances(UUID socioId) {
        return acceptanceRepository.findBySocioIdOrderByAcceptedAtDesc(socioId)
                .stream()
                .map(a -> toResponse(a, a.getLegalDocument().getTitle()))
                .toList();
    }

    // -------------------------------------------------------------------------

    private LegalDocumentAcceptanceResponse toResponse(LegalDocumentAcceptance a, String title) {
        return new LegalDocumentAcceptanceResponse(
                a.getId(),
                a.getLegalDocument().getId(),
                a.getDocumentCode(),
                title,
                a.getDocumentVersion(),
                a.getAcceptedAt(),
                a.isAccepted());
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
