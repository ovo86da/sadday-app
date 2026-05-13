package com.sadday.app.auth.service;

import com.sadday.app.auth.dto.ApiKeyResponse;
import com.sadday.app.auth.dto.CreateApiKeyRequest;
import com.sadday.app.auth.dto.CreateApiKeyResponse;
import com.sadday.app.auth.entity.ApiKey;
import com.sadday.app.auth.repository.ApiKeyRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyService — Unit Tests")
class ApiKeyServiceTest {

    @Mock ApiKeyRepository apiKeyRepository;

    @InjectMocks ApiKeyService apiKeyService;

    private final UUID socioId = UUID.randomUUID();

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void creaKeyCorrectamente() {
            when(apiKeyRepository.countActiveBySocioId(eq(socioId), any())).thenReturn(0L);

            ApiKey saved = ApiKey.builder()
                    .id(UUID.randomUUID())
                    .socioId(socioId)
                    .nombre("Mi App")
                    .keyHash("somehash")
                    .createdAt(OffsetDateTime.now())
                    .expiresAt(OffsetDateTime.now().plusYears(1))
                    .build();
            when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(saved);

            CreateApiKeyResponse response = apiKeyService.create(socioId, new CreateApiKeyRequest("Mi App"));

            assertNotNull(response);
            assertEquals("Mi App", response.nombre());
            assertTrue(response.key().startsWith("sk-sadday-"));
        }

        @Test
        void limiteAlcanzado_lanzaLimitReached() {
            when(apiKeyRepository.countActiveBySocioId(eq(socioId), any())).thenReturn(5L);

            var ex = assertThrows(BusinessException.class, () ->
                    apiKeyService.create(socioId, new CreateApiKeyRequest("Nueva Key"))
            );
            assertEquals(ErrorCode.API_KEY_LIMIT_REACHED, ex.getErrorCode());
        }

        @Test
        void rawKeyNoSePersisteEnBD() {
            when(apiKeyRepository.countActiveBySocioId(eq(socioId), any())).thenReturn(0L);
            ApiKey saved = ApiKey.builder()
                    .id(UUID.randomUUID()).socioId(socioId).nombre("Test")
                    .keyHash("hash").createdAt(OffsetDateTime.now())
                    .expiresAt(OffsetDateTime.now().plusYears(1)).build();
            when(apiKeyRepository.save(any())).thenReturn(saved);

            apiKeyService.create(socioId, new CreateApiKeyRequest("Test"));

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(captor.capture());
            assertNull(captor.getValue().getRevokedAt());
            assertNotNull(captor.getValue().getKeyHash());
        }
    }

    @Nested
    @DisplayName("listActive")
    class ListActive {

        @Test
        void soloDevolverKeysActivas() {
            ApiKey activa = ApiKey.builder()
                    .id(UUID.randomUUID()).socioId(socioId).nombre("Activa")
                    .keyHash("h1").createdAt(OffsetDateTime.now())
                    .expiresAt(OffsetDateTime.now().plusYears(1)).build();
            ApiKey revocada = ApiKey.builder()
                    .id(UUID.randomUUID()).socioId(socioId).nombre("Revocada")
                    .keyHash("h2").createdAt(OffsetDateTime.now())
                    .expiresAt(OffsetDateTime.now().plusYears(1))
                    .revokedAt(OffsetDateTime.now().minusDays(1)).build();

            when(apiKeyRepository.findBySocioIdOrderByCreatedAtDesc(socioId))
                    .thenReturn(List.of(activa, revocada));

            List<ApiKeyResponse> result = apiKeyService.listActive(socioId);

            assertEquals(1, result.size());
            assertEquals("Activa", result.get(0).nombre());
        }

        @Test
        void sinKeys_retornaListaVacia() {
            when(apiKeyRepository.findBySocioIdOrderByCreatedAtDesc(socioId))
                    .thenReturn(List.of());

            assertTrue(apiKeyService.listActive(socioId).isEmpty());
        }
    }

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        void keyPropia_setRevokedAt() {
            UUID keyId = UUID.randomUUID();
            ApiKey key = ApiKey.builder()
                    .id(keyId).socioId(socioId).nombre("K")
                    .keyHash("h").createdAt(OffsetDateTime.now())
                    .expiresAt(OffsetDateTime.now().plusYears(1)).build();

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            apiKeyService.revoke(keyId, socioId);

            assertNotNull(key.getRevokedAt());
        }

        @Test
        void keyDeOtroSocio_lanzaNotFound() {
            UUID keyId = UUID.randomUUID();
            ApiKey key = ApiKey.builder()
                    .id(keyId).socioId(UUID.randomUUID()).nombre("K")
                    .keyHash("h").createdAt(OffsetDateTime.now())
                    .expiresAt(OffsetDateTime.now().plusYears(1)).build();

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            var ex = assertThrows(BusinessException.class, () ->
                    apiKeyService.revoke(keyId, socioId)
            );
            assertEquals(ErrorCode.API_KEY_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void keyNoExiste_lanzaNotFound() {
            UUID keyId = UUID.randomUUID();
            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () ->
                    apiKeyService.revoke(keyId, socioId)
            );
            assertEquals(ErrorCode.API_KEY_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("findActiveByRawKey")
    class FindActiveByRawKey {

        @Test
        void keyActiva_retornaPresent() {
            ApiKey key = ApiKey.builder()
                    .id(UUID.randomUUID()).socioId(socioId).nombre("K")
                    .keyHash("hash").createdAt(OffsetDateTime.now())
                    .expiresAt(OffsetDateTime.now().plusYears(1)).build();

            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(key));

            assertTrue(apiKeyService.findActiveByRawKey("sk-sadday-somekey").isPresent());
        }

        @Test
        void keyRevocada_retornaEmpty() {
            ApiKey key = ApiKey.builder()
                    .id(UUID.randomUUID()).socioId(socioId).nombre("K")
                    .keyHash("hash").createdAt(OffsetDateTime.now())
                    .expiresAt(OffsetDateTime.now().plusYears(1))
                    .revokedAt(OffsetDateTime.now().minusDays(1)).build();

            when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(key));

            assertTrue(apiKeyService.findActiveByRawKey("sk-sadday-revoked").isEmpty());
        }
    }
}
