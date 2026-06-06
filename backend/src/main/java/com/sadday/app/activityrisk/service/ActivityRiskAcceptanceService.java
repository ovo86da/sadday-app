package com.sadday.app.activityrisk.service;

import com.sadday.app.activityrisk.dto.ActivityRiskAcceptanceResponse;
import com.sadday.app.activityrisk.entity.ActivityRiskAcceptance;
import com.sadday.app.activityrisk.entity.ActivityRiskDocument;
import com.sadday.app.activityrisk.repository.ActivityRiskAcceptanceRepository;
import com.sadday.app.activityrisk.repository.ActivityRiskDocumentRepository;
import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.salidas.repository.SalidaRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ActivityRiskAcceptanceService {

    private final ActivityRiskAcceptanceRepository acceptanceRepository;
    private final ActivityRiskDocumentRepository   riskDocRepository;
    private final SalidaRepository                 salidaRepository;
    private final SocioRepository                  socioRepository;

    @PreAuthorize("isAuthenticated()")
    public ActivityRiskAcceptanceResponse aceptar(UUID activityId, UUID socioId, String ip, String userAgent) {
        Salida salida = salidaRepository.findById(activityId)
                .filter(s -> !s.isEliminada())
                .orElseThrow(() -> new BusinessException(ErrorCode.SALIDA_NOT_FOUND));

        ActivityRiskDocument riskDoc = riskDocRepository.findByActivityIdAndActiveTrue(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_RISK_NOT_FOUND));

        if (acceptanceRepository.existsBySocioIdAndActivityIdAndRiskDocumentId(
                socioId, activityId, riskDoc.getId())) {
            throw new BusinessException(ErrorCode.ACTIVITY_RISK_ALREADY_ACCEPTED);
        }

        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        ActivityRiskAcceptance acceptance = ActivityRiskAcceptance.builder()
                .socio(socio)
                .activity(salida)
                .riskDocument(riskDoc)
                .documentVersion(riskDoc.getVersion())
                .contentHash(riskDoc.getContentHash())
                .ipAddress(ip)
                .userAgent(userAgent)
                .build();

        ActivityRiskAcceptance saved = acceptanceRepository.save(acceptance);
        return toResponse(saved);
    }

    /** Comprueba si el socio ha aceptado el documento de riesgo activo de la salida. */
    @Transactional(readOnly = true)
    public boolean haAceptadoDocumentoActivo(UUID socioId, UUID activityId, UUID riskDocumentId) {
        return acceptanceRepository.existsBySocioIdAndActivityIdAndRiskDocumentId(
                socioId, activityId, riskDocumentId);
    }

    private ActivityRiskAcceptanceResponse toResponse(ActivityRiskAcceptance a) {
        return new ActivityRiskAcceptanceResponse(
                a.getId(),
                a.getSocio().getId(),
                a.getActivity().getId(),
                a.getRiskDocument().getId(),
                a.getDocumentVersion(),
                a.getAcceptedAt()
        );
    }
}
