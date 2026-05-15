package com.sadday.app.admin.service;

import com.sadday.app.admin.dto.ConfiguracionSistemaResponse;
import com.sadday.app.admin.dto.UpdateConfigRequest;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.shared.entity.ConfiguracionSistema;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.repository.ConfiguracionSistemaRepository;
import com.sadday.app.shared.util.ClientIpExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracionSistemaService {

    private final ConfiguracionSistemaRepository repo;
    private final AuditService auditService;
    private final ClientIpExtractor clientIpExtractor;

    public List<ConfiguracionSistemaResponse> listar() {
        return repo.findAll().stream()
                .map(ConfiguracionSistemaResponse::from)
                .toList();
    }

    public ConfiguracionSistemaResponse obtener(String clave) {
        return repo.findByClave(clave)
                .map(ConfiguracionSistemaResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Configuración no encontrada: " + clave));
    }

    @Transactional
    public ConfiguracionSistemaResponse actualizar(String clave, UpdateConfigRequest request,
                                                   Authentication auth) {
        ConfiguracionSistema config = repo.findByClave(clave)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Configuración no encontrada: " + clave));

        String valorAnterior = config.getValor();

        UUID actorId = extractSocioId(auth);
        registrarConSnapshot(auth.getName(), clave, valorAnterior, request.valor());

        config.setValor(request.valor());
        config.setUpdatedById(actorId);
        config.setUpdatedAt(LocalDateTime.now());
        repo.save(config);

        log.info("Configuración '{}' actualizada: '{}' → '{}' por {}",
                clave, valorAnterior, request.valor(), auth.getName());

        return ConfiguracionSistemaResponse.from(config);
    }

    // -------------------------------------------------------------------------

    private void registrarConSnapshot(String actor, String clave,
                                      String valorAnterior, String valorNuevo) {
        String antes   = toJson(clave, valorAnterior);
        String despues = toJson(clave, valorNuevo);
        String detalle = "Parámetro '" + clave + "' cambiado de '" + valorAnterior + "' a '" + valorNuevo + "'";
        auditService.registrar(actor, "UPDATE_CONFIG", "configuracion_sistema",
                clave, antes, despues,
                clientIpExtractor.extractIpFromContext(),
                clientIpExtractor.extractUserAgentFromContext(),
                "SUCCESS", detalle);
    }

    private static String toJson(String clave, String valor) {
        // Escapar comillas dobles para mantener JSON válido sin dependencia de ObjectMapper.
        String k = clave  == null ? "" : clave.replace("\"", "\\\"");
        String v = valor  == null ? "" : valor.replace("\"", "\\\"");
        return "{\"clave\":\"" + k + "\",\"valor\":\"" + v + "\"}";
    }

    private UUID extractSocioId(Authentication auth) {
        try {
            Object details = auth.getDetails();
            if (details instanceof com.sadday.app.security.jwt.SaddayAuthDetails sad) {
                return sad.socioId();
            }
        } catch (Exception ignored) {
            log.warn("Error enviando correo de prueba: {}", ignored.getMessage(), ignored);
        }
        return null;
    }
}
