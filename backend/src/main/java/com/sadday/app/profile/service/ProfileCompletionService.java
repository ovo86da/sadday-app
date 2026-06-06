package com.sadday.app.profile.service;

import com.sadday.app.emergencycontacts.repository.SocioEmergencyContactRepository;
import com.sadday.app.legal.entity.LegalDocument;
import com.sadday.app.legal.repository.LegalDocumentAcceptanceRepository;
import com.sadday.app.legal.repository.LegalDocumentRepository;
import com.sadday.app.medicalinfo.repository.SocioMedicalInfoRepository;
import com.sadday.app.profile.dto.ProfileCompletionStatusResponse;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileCompletionService {

    private final SocioRepository                    socioRepository;
    private final SocioEmergencyContactRepository    contactRepository;
    private final SocioMedicalInfoRepository         medicalInfoRepository;
    private final LegalDocumentRepository            documentRepository;
    private final LegalDocumentAcceptanceRepository  acceptanceRepository;

    @PreAuthorize("isAuthenticated()")
    public ProfileCompletionStatusResponse getMiStatus(UUID socioId) {
        return getStatus(socioId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ProfileCompletionStatusResponse getStatusBySocioId(UUID socioId) {
        return getStatus(socioId);
    }

    /** Sin control de acceso — llamado internamente desde SalidaService. */
    public ProfileCompletionStatusResponse getStatus(UUID socioId) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        List<String> missing  = new ArrayList<>();
        List<String> pending  = new ArrayList<>();
        List<String> expired  = new ArrayList<>();

        // 1. Datos básicos
        if (socio.getTelefono() == null || socio.getTelefono().isBlank()) {
            missing.add("Debe completar su número de teléfono");
        }

        // 2. Contactos de emergencia (2 requeridos)
        long numContactos = contactRepository.countBySocioId(socioId);
        if (numContactos < 2) {
            missing.add("Debe registrar 2 contactos de emergencia (tiene " + numContactos + ")");
        }

        // 3. Información médica
        if (!medicalInfoRepository.existsBySocioId(socioId)) {
            missing.add("Debe completar su información médica");
        }

        // 4–7. Documentos legales obligatorios activos
        List<LegalDocument> requiredDocs = documentRepository.findActiveRequired();
        for (LegalDocument doc : requiredDocs) {
            Optional<Integer> latestAccepted =
                    acceptanceRepository.findLatestAcceptedVersion(socioId, doc.getCode());

            if (latestAccepted.isEmpty()) {
                pending.add(doc.getCode());
                missing.add("Debe aceptar: " + doc.getTitle());
            } else if (doc.isRequiresReacceptanceOnNewVersion()
                       && latestAccepted.get() < doc.getVersion()) {
                expired.add(doc.getCode());
                missing.add("Debe re-aceptar la nueva versión de: " + doc.getTitle());
            }
        }

        boolean complete = missing.isEmpty();
        return new ProfileCompletionStatusResponse(complete, complete, missing, pending, expired);
    }
}
