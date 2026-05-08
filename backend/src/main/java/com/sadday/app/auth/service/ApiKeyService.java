package com.sadday.app.auth.service;

import com.sadday.app.auth.dto.ApiKeyResponse;
import com.sadday.app.auth.dto.CreateApiKeyRequest;
import com.sadday.app.auth.dto.CreateApiKeyResponse;
import com.sadday.app.auth.entity.ApiKey;
import com.sadday.app.auth.repository.ApiKeyRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ApiKeyService {

    private static final int MAX_ACTIVE_KEYS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    public CreateApiKeyResponse create(UUID socioId, CreateApiKeyRequest request) {
        long active = apiKeyRepository.countActiveBySocioId(socioId, OffsetDateTime.now());
        if (active >= MAX_ACTIVE_KEYS) {
            throw new BusinessException(ErrorCode.API_KEY_LIMIT_REACHED);
        }

        byte[] rawBytes = new byte[32];
        SECURE_RANDOM.nextBytes(rawBytes);
        String rawKey = "sk-sadday-" + Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
        String keyHash = sha256Hex(rawKey);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusYears(1);

        ApiKey entity = ApiKey.builder()
                .socioId(socioId)
                .nombre(request.nombre())
                .keyHash(keyHash)
                .expiresAt(expiresAt)
                .build();

        entity = apiKeyRepository.save(entity);
        log.info("API key creada: socioId={} nombre={}", socioId, request.nombre());

        return new CreateApiKeyResponse(entity.getId(), entity.getNombre(), rawKey,
                entity.getCreatedAt(), entity.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listActive(UUID socioId) {
        return apiKeyRepository.findBySocioIdOrderByCreatedAtDesc(socioId).stream()
                .filter(ApiKey::isActive)
                .map(k -> new ApiKeyResponse(k.getId(), k.getNombre(),
                        k.getCreatedAt(), k.getExpiresAt(), k.getLastUsedAt()))
                .toList();
    }

    public void revoke(UUID keyId, UUID requestingSocioId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_NOT_FOUND));

        // Devuelve 404 si la key no pertenece al usuario — evita enumerar keys de otros
        if (!key.getSocioId().equals(requestingSocioId)) {
            throw new BusinessException(ErrorCode.API_KEY_NOT_FOUND);
        }

        key.setRevokedAt(OffsetDateTime.now());
        log.info("API key revocada: id={} socioId={}", keyId, requestingSocioId);
    }

    /** Busca una key activa por su valor raw. Solo consulta, no actualiza lastUsedAt. */
    @Transactional(readOnly = true)
    public java.util.Optional<ApiKey> findActiveByRawKey(String rawKey) {
        String hash = sha256Hex(rawKey);
        return apiKeyRepository.findByKeyHash(hash)
                .filter(ApiKey::isActive);
    }

    @Transactional
    public void touchLastUsedAt(UUID keyId) {
        apiKeyRepository.updateLastUsedAt(keyId, OffsetDateTime.now());
    }

    // -------------------------------------------------------------------------

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
