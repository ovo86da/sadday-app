package com.sadday.app.emergencycontacts.service;

import com.sadday.app.audit.AuditAction;
import com.sadday.app.audit.DocumentAuditService;
import com.sadday.app.emergencycontacts.dto.EmergencyContactRequest;
import com.sadday.app.emergencycontacts.dto.EmergencyContactResponse;
import com.sadday.app.emergencycontacts.dto.UpsertEmergencyContactsRequest;
import com.sadday.app.emergencycontacts.entity.SocioEmergencyContact;
import com.sadday.app.emergencycontacts.repository.SocioEmergencyContactRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmergencyContactService {

    private final SocioEmergencyContactRepository contactRepository;
    private final SocioRepository                 socioRepository;
    private final DocumentAuditService            documentAuditService;

    // -------------------------------------------------------------------------
    // Consulta
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<EmergencyContactResponse> getMisPropios(UUID socioId) {
        return contactRepository.findBySocioIdOrderByOrden(socioId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public List<EmergencyContactResponse> getBySocioId(UUID socioId) {
        if (!socioRepository.existsById(socioId)) {
            throw new BusinessException(ErrorCode.SOCIO_NOT_FOUND);
        }
        return contactRepository.findBySocioIdOrderByOrden(socioId)
                .stream().map(this::toResponse).toList();
    }

    // -------------------------------------------------------------------------
    // Upsert (reemplaza todos los contactos del socio)
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public List<EmergencyContactResponse> upsertMisPropios(UUID socioId,
                                                           UpsertEmergencyContactsRequest req) {
        return doUpsert(socioId, req);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public List<EmergencyContactResponse> upsertBySocioId(UUID socioId,
                                                          UpsertEmergencyContactsRequest req) {
        if (!socioRepository.existsById(socioId)) {
            throw new BusinessException(ErrorCode.SOCIO_NOT_FOUND);
        }
        return doUpsert(socioId, req);
    }

    // -------------------------------------------------------------------------

    private List<EmergencyContactResponse> doUpsert(UUID socioId, UpsertEmergencyContactsRequest req) {
        validarOrdenes(req.contactos());

        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        contactRepository.deleteBySocioId(socioId);
        contactRepository.flush();

        List<SocioEmergencyContact> saved = req.contactos().stream()
                .map(c -> contactRepository.save(SocioEmergencyContact.builder()
                        .socio(socio)
                        .orden(c.orden())
                        .nombreCompleto(c.nombreCompleto())
                        .relacion(c.relacion())
                        .celular(c.celular())
                        .direccion(c.direccion())
                        .build()))
                .toList();

        List<EmergencyContactResponse> responses = saved.stream().map(this::toResponse).toList();
        documentAuditService.log(AuditAction.EMERGENCY_CONTACT_UPDATED, "EMERGENCY_CONTACTS", socioId);
        return responses;
    }

    private void validarOrdenes(List<EmergencyContactRequest> contactos) {
        Set<Short> ordenes = contactos.stream()
                .map(EmergencyContactRequest::orden)
                .collect(Collectors.toSet());
        if (ordenes.size() != contactos.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Los órdenes de los contactos deben ser únicos (1 y/o 2)");
        }
    }

    private EmergencyContactResponse toResponse(SocioEmergencyContact c) {
        return new EmergencyContactResponse(
                c.getId(), c.getOrden(), c.getNombreCompleto(),
                c.getRelacion(), c.getCelular(), c.getDireccion());
    }
}
