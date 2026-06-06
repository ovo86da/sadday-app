package com.sadday.app.activityrisk.service;

import com.sadday.app.activityrisk.dto.ActivityRiskDocumentResponse;
import com.sadday.app.activityrisk.dto.CreateActivityRiskDocumentRequest;
import com.sadday.app.activityrisk.entity.ActivityRiskDocument;
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ActivityRiskDocumentService {

    private final ActivityRiskDocumentRepository riskDocRepository;
    private final SalidaRepository               salidaRepository;
    private final SocioRepository                socioRepository;

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ActivityRiskDocumentResponse getActivoParaSalida(UUID activityId) {
        return riskDocRepository.findByActivityIdAndActiveTrue(activityId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_RISK_NOT_FOUND));
    }

    /** Variante interna sin excepción — usada desde la validación de inscripción. */
    @Transactional(readOnly = true)
    public Optional<ActivityRiskDocument> findActivo(UUID activityId) {
        return riskDocRepository.findByActivityIdAndActiveTrue(activityId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public ActivityRiskDocumentResponse crear(UUID activityId, CreateActivityRiskDocumentRequest request, UUID actorId) {
        Salida salida = salidaRepository.findById(activityId)
                .filter(s -> !s.isEliminada())
                .orElseThrow(() -> new BusinessException(ErrorCode.SALIDA_NOT_FOUND));

        Socio actor = socioRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        int nextVersion = riskDocRepository.findMaxVersionByActivityId(activityId)
                .map(v -> v + 1).orElse(1);

        String hash = sha256(request.content());

        ActivityRiskDocument doc = ActivityRiskDocument.builder()
                .activity(salida)
                .version(nextVersion)
                .content(request.content())
                .contentHash(hash)
                .active(true)
                .approvedAt(LocalDateTime.now())
                .approvedBy(actor)
                .build();

        ActivityRiskDocument saved = riskDocRepository.save(doc);

        // Desactivar versiones anteriores
        riskDocRepository.deactivateOtherVersions(activityId, saved.getId());

        return toResponse(saved);
    }

    private ActivityRiskDocumentResponse toResponse(ActivityRiskDocument doc) {
        return new ActivityRiskDocumentResponse(
                doc.getId(),
                doc.getActivity().getId(),
                doc.getVersion(),
                doc.getContent(),
                doc.getContentHash(),
                doc.isActive(),
                doc.getApprovedAt(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
