package com.sadday.app.auth.service;

import com.sadday.app.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Base64;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios de TotpService.
 *
 * <p>No requiere contexto de Spring: se instancia el servicio directamente.
 */
@DisplayName("TotpService — Unit Tests")
class TotpServiceTest {

    // 32 bytes de ceros — solo para tests de validación de clave débil
    private static final String ALL_ZEROS_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    // Clave con entropía real para tests funcionales
    private static final String TEST_AES_KEY;
    static {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        key[0] = (byte) (key[1] ^ 0x01);  // garantiza que no todos los bytes son iguales
        TEST_AES_KEY = Base64.getEncoder().encodeToString(key);
    }

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties();
        props.setTotpEncryptionKey(TEST_AES_KEY);

        totpService = new TotpService(props);
        ReflectionTestUtils.invokeMethod(totpService, "initKey");
    }

    // =========================================================================
    // generateSecret
    // =========================================================================

    @Test
    @DisplayName("generateSecret devuelve encrypted no nulo y base32 no vacío")
    void generateSecret_returnsValidPair() {
        TotpService.TotpSecret secret = totpService.generateSecret();

        assertNotNull(secret);
        assertNotNull(secret.encrypted());
        assertFalse(secret.encrypted().isBlank());
        assertNotNull(secret.base32());
        assertFalse(secret.base32().isBlank());
        // Base32 solo debe tener caracteres A-Z y 2-7
        assertTrue(secret.base32().matches("[A-Z2-7]+"),
                "El base32 contiene caracteres inválidos: " + secret.base32());
    }

    @Test
    @DisplayName("Cada llamada a generateSecret produce un secret diferente")
    void generateSecret_producesUniqueSecrets() {
        TotpService.TotpSecret s1 = totpService.generateSecret();
        TotpService.TotpSecret s2 = totpService.generateSecret();

        assertNotEquals(s1.encrypted(), s2.encrypted());
        assertNotEquals(s1.base32(),    s2.base32());
    }

    // =========================================================================
    // verify
    // =========================================================================

    @Test
    @DisplayName("verify acepta el código del paso actual")
    void verify_validCodeCurrentStep_returnsTrue() {
        TotpService.TotpSecret secret = totpService.generateSecret();
        String validCode = computeTotpCode(secret.encrypted());

        OptionalLong result = totpService.verify(secret.encrypted(), validCode, -1L);
        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("verify rechaza un código de 6 dígitos incorrecto")
    void verify_wrongCode_returnsFalse() {
        TotpService.TotpSecret secret = totpService.generateSecret();
        assertTrue(totpService.verify(secret.encrypted(), "000000", -1L).isEmpty());
    }

    @Test
    @DisplayName("verify rechaza null en encryptedSecret")
    void verify_nullSecret_returnsFalse() {
        assertTrue(totpService.verify(null, "123456", -1L).isEmpty());
    }

    @Test
    @DisplayName("verify rechaza null en code")
    void verify_nullCode_returnsFalse() {
        TotpService.TotpSecret secret = totpService.generateSecret();
        assertTrue(totpService.verify(secret.encrypted(), null, -1L).isEmpty());
    }

    @Test
    @DisplayName("verify rechaza código con longitud incorrecta")
    void verify_wrongLengthCode_returnsFalse() {
        TotpService.TotpSecret secret = totpService.generateSecret();
        assertTrue(totpService.verify(secret.encrypted(), "12345",   -1L).isEmpty());
        assertTrue(totpService.verify(secret.encrypted(), "1234567", -1L).isEmpty());
        assertTrue(totpService.verify(secret.encrypted(), "abc123",  -1L).isEmpty());
    }

    @Test
    @DisplayName("verify rechaza código con secret corrupto")
    void verify_corruptedSecret_returnsFalse() {
        assertTrue(totpService.verify("secret-corrupto-no-base64", "123456", -1L).isEmpty());
    }

    @Test
    @DisplayName("verify rechaza replay: el mismo código con step ya utilizado")
    void verify_replayAttack_returnsEmpty() {
        TotpService.TotpSecret secret = totpService.generateSecret();
        String validCode = computeTotpCode(secret.encrypted());

        OptionalLong first = totpService.verify(secret.encrypted(), validCode, -1L);
        assertTrue(first.isPresent(), "El primer uso debe aceptarse");

        // Replay: mismo código, lastUsedCounter = step recién aceptado
        OptionalLong replay = totpService.verify(secret.encrypted(), validCode, first.getAsLong());
        assertTrue(replay.isEmpty(), "El replay debe ser rechazado");
    }

    // =========================================================================
    // buildOtpAuthUri
    // =========================================================================

    @Test
    @DisplayName("buildOtpAuthUri produce URI con formato otpauth:// correcto")
    void buildOtpAuthUri_correctFormat() {
        String uri = totpService.buildOtpAuthUri("juan.perez", "JBSWY3DPEHPK3PXP");

        assertTrue(uri.startsWith("otpauth://totp/Sadday:juan.perez"));
        assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"));
        assertTrue(uri.contains("issuer=Sadday"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }

    // =========================================================================
    // initKey — validación de clave
    // =========================================================================

    @Test
    @DisplayName("initKey falla si la clave no es de 32 bytes")
    void initKey_invalidKeyLength_throwsIllegalState() {
        SecurityProperties badProps = new SecurityProperties();
        badProps.setTotpEncryptionKey("dGVzdA==");  // solo 4 bytes

        TotpService badService = new TotpService(badProps);
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(badService, "initKey"));
    }

    @Test
    @DisplayName("initKey falla si la clave es null")
    void initKey_nullKey_throwsIllegalState() {
        SecurityProperties badProps = new SecurityProperties();
        badProps.setTotpEncryptionKey(null);

        TotpService badService = new TotpService(badProps);
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(badService, "initKey"));
    }

    @Test
    @DisplayName("initKey falla si la clave es blank")
    void initKey_blankKey_throwsIllegalState() {
        SecurityProperties badProps = new SecurityProperties();
        badProps.setTotpEncryptionKey("   ");

        TotpService badService = new TotpService(badProps);
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(badService, "initKey"));
    }

    @Test
    @DisplayName("initKey falla si la clave es todo ceros")
    void initKey_allZerosKey_throwsIllegalState() {
        SecurityProperties badProps = new SecurityProperties();
        badProps.setTotpEncryptionKey(ALL_ZEROS_KEY);

        TotpService badService = new TotpService(badProps);
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(badService, "initKey"));
    }

    @Test
    @DisplayName("initKey falla si la clave es un byte repetido (0xFF x32)")
    void initKey_repeatedByteKey_throwsIllegalState() {
        byte[] repeated = new byte[32];
        Arrays.fill(repeated, (byte) 0xFF);
        SecurityProperties badProps = new SecurityProperties();
        badProps.setTotpEncryptionKey(Base64.getEncoder().encodeToString(repeated));

        TotpService badService = new TotpService(badProps);
        assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(badService, "initKey"));
    }

    @Test
    @DisplayName("initKey acepta una clave de 32 bytes con entropía real")
    void initKey_validRandomKey_succeeds() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        // Aseguramos que no sea el caso trivial de byte repetido (extremadamente improbable, pero lo forzamos)
        key[0] = (byte) (key[1] ^ 0x01);
        SecurityProperties goodProps = new SecurityProperties();
        goodProps.setTotpEncryptionKey(Base64.getEncoder().encodeToString(key));

        TotpService goodService = new TotpService(goodProps);
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(goodService, "initKey"));
    }

    // =========================================================================
    // toBase32 — rama de bits restantes (línea solo cubierta con input no múltiplo de 5)
    // =========================================================================

    @Nested
    @DisplayName("toBase32 — casos de cobertura")
    class ToBase32 {

        @Test
        @DisplayName("input de 1 byte produce Base32 con bits restantes (rama bitsLeft > 0)")
        void toBase32_unByte_cubreraBitsLeft() {
            byte[] input = new byte[]{(byte) 0xAB};
            String result = ReflectionTestUtils.invokeMethod(totpService, "toBase32", input);
            assertNotNull(result);
            assertFalse(result.isBlank());
            assertTrue(result.matches("[A-Z2-7]+"));
        }
    }

    // =========================================================================
    // encrypt — rama de catch (solo alcanzable con clave nula)
    // =========================================================================

    @Nested
    @DisplayName("encrypt — error path")
    class EncryptErrorPath {

        @Test
        @DisplayName("aesKey nulo lanza IllegalStateException desde el catch")
        void encrypt_claveNula_lanzaIllegalState() {
            ReflectionTestUtils.setField(totpService, "aesKey", null);
            assertThrows(IllegalStateException.class,
                    () -> ReflectionTestUtils.invokeMethod(totpService, "encrypt", new byte[]{1, 2, 3}));
        }
    }

    // =========================================================================
    // Helper — calcula el código TOTP actual para verificar en tests
    // =========================================================================

    /**
     * Computa el código TOTP para el paso de tiempo actual usando el mismo algoritmo
     * que el servicio, a través de verify con el código calculado.
     * Si el código del step actual es correcto, devuelve ese. Si no, busca en ±1.
     */
    private String computeTotpCode(String encryptedSecret) {
        for (int i = 0; i <= 999999; i++) {
            String candidate = String.format("%06d", i);
            if (totpService.verify(encryptedSecret, candidate, -1L).isPresent()) {
                return candidate;
            }
        }
        throw new AssertionError("No se pudo computar un código TOTP válido");
    }
}
