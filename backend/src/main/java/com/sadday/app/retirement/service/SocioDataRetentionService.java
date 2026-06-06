package com.sadday.app.retirement.service;

import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.emergencycontacts.repository.SocioEmergencyContactRepository;
import com.sadday.app.medicalinfo.repository.SocioMedicalInfoRepository;
import com.sadday.app.retirement.dto.RetireSocioResponse;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.EstadoAcceso;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.EstadoAccesoRepository;
import com.sadday.app.socios.repository.EstadoCuotaRepository;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SocioDataRetentionService {

    private static final String ESTADO_EX_MEMBER = "EX_MEMBER";
    private static final String ESTADO_PENDIENTE  = "PENDIENTE";

    private final SocioRepository                socioRepository;
    private final EstadoAccesoRepository         estadoAccesoRepository;
    private final EstadoCuotaRepository          cuotaRepository;
    private final SocioEmergencyContactRepository contactRepository;
    private final SocioMedicalInfoRepository      medicalInfoRepository;
    private final UsuarioAuthRepository           usuarioAuthRepository;
    private final PasswordEncoder                 passwordEncoder;

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public RetireSocioResponse retireSocio(UUID socioId, String reason, UUID actorId) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        if (actorId.equals(socioId)) {
            throw new BusinessException(ErrorCode.CANNOT_RETIRE_SELF);
        }

        if (ESTADO_EX_MEMBER.equals(socio.getEstadoAcceso().getCodigo())) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_RETIRED);
        }

        List<String> actions = new ArrayList<>();

        // 1. Eliminar datos sensibles (siempre, independiente de la deuda)
        deleteEmergencyContacts(socioId, actions);
        deleteMedicalInfo(socioId, actions);

        // 2. Cambiar estado de acceso a EX_MEMBER
        EstadoAcceso exMember = estadoAccesoRepository.findByCodigo(ESTADO_EX_MEMBER)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Estado EX_MEMBER no encontrado en la base de datos"));
        socio.setEstadoAcceso(exMember);
        socio.setFechaSalida(LocalDate.now());
        actions.add("Estado de acceso cambiado a EX_MEMBER");
        actions.add("Fecha de salida registrada: " + socio.getFechaSalida());

        // 3. Verificar deuda pendiente y actuar según corresponda
        boolean hasPendingDebt = checkPendingFinancialObligations(socioId);
        if (hasPendingDebt) {
            retainMinimalAdministrativeRecord(socioId, actions);
        } else {
            anonymizeNonRequiredHistoricalData(socio, actions);
        }

        socioRepository.save(socio);

        // 4. Desactivar cuenta de autenticación
        deactivateAuthAccount(socioId, actions);

        log.info("Socio retirado: id={}, actor={}, deuda={}, motivo={}",
                socioId, actorId, hasPendingDebt, reason);

        return new RetireSocioResponse(
                socio.getId(),
                socio.getNombre(),
                socio.getApellido(),
                socio.getFechaSalida(),
                hasPendingDebt,
                List.copyOf(actions),
                LocalDateTime.now()
        );
    }

    /** Elimina los contactos de emergencia del socio. */
    public void deleteEmergencyContacts(UUID socioId, List<String> actions) {
        long count = contactRepository.countBySocioId(socioId);
        contactRepository.deleteBySocioId(socioId);
        if (count > 0) {
            actions.add("Eliminados " + count + " contacto(s) de emergencia");
        }
    }

    /** Elimina la información médica del socio. */
    public void deleteMedicalInfo(UUID socioId, List<String> actions) {
        if (medicalInfoRepository.existsBySocioId(socioId)) {
            medicalInfoRepository.deleteBySocioId(socioId);
            actions.add("Información médica eliminada");
        }
    }

    /** Retorna true si el socio tiene cuotas con estado PENDIENTE. */
    public boolean checkPendingFinancialObligations(UUID socioId) {
        return cuotaRepository.existsBySocioIdAndEstado(socioId, ESTADO_PENDIENTE);
    }

    /**
     * Caso con deuda: conserva correo y teléfono para gestionar la obligación financiera.
     * Limpia solo dirección (no necesaria para cobro).
     */
    public void retainMinimalAdministrativeRecord(UUID socioId, List<String> actions) {
        socioRepository.findById(socioId).ifPresent(socio -> {
            socio.setDireccion(null);
            socioRepository.save(socio);
        });
        actions.add("Deuda pendiente detectada: correo y teléfono conservados para gestión financiera");
        actions.add("Dirección eliminada");
    }

    /**
     * Caso sin deuda: anonimiza correo (NOT NULL), elimina teléfono y dirección.
     */
    public void anonymizeNonRequiredHistoricalData(Socio socio, List<String> actions) {
        // Correo es NOT NULL y UNIQUE — lo reemplazamos con un placeholder irrastreable
        socio.setCorreo("retirado_" + socio.getId() + "@sadday.invalid");
        socio.setTelefono(null);
        socio.setDireccion(null);
        actions.add("Correo anonimizado, teléfono y dirección eliminados (sin deuda pendiente)");
    }

    /** Bloquea la cuenta de auth: loginBlocked=true, TOTP desactivado, contraseña invalidada. */
    private void deactivateAuthAccount(UUID socioId, List<String> actions) {
        usuarioAuthRepository.findBySocioId(socioId).ifPresent(auth -> {
            auth.setLoginBlocked(true);
            auth.setTotpEnabled(false);
            auth.setTotpSecret(null);
            // Reemplazar el hash de contraseña con uno irrecuperable
            auth.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            usuarioAuthRepository.save(auth);
            actions.add("Cuenta de autenticación desactivada");
        });
    }
}
