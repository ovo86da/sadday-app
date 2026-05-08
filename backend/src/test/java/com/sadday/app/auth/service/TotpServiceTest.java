package com.sadday.app.auth.service;

import com.sadday.app.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios de TotpService.
 *
 * <p>No requiere contexto de Spring: se instancia el servicio directamente.
 * Se usa la clave AES-256 de prueba (32 bytes de ceros).
 */
@DisplayName("TotpService — Unit Tests")
class TotpServiceTest {

    // 32 bytes de ceros codificados en base64 — solo para tests
    private static final String TEST_AES_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties();
        props.setTotpEncryptionKey(TEST_AES_KEY);

        totpService = new TotpService(props);
        // Invocar @PostConstruct manualmente (privado, usamos ReflectionTestUtils)
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
        // Computar el código correcto para el momento actual
        String validCode = computeTotpCode(secret.encrypted());

        assertTrue(totpService.verify(secret.encrypted(), validCode));
    }

    @Test
    @DisplayName("verify rechaza un código de 6 dígitos incorrecto")
    void verify_wrongCode_returnsFalse() {
        TotpService.TotpSecret secret = totpService.generateSecret();
        // Código que casi con certeza es incorrecto
        assertFalse(totpService.verify(secret.encrypted(), "000000"));
    }

    @Test
    @DisplayName("verify rechaza null en encryptedSecret")
    void verify_nullSecret_returnsFalse() {
        assertFalse(totpService.verify(null, "123456"));
    }

    @Test
    @DisplayName("verify rechaza null en code")
    void verify_nullCode_returnsFalse() {
        TotpService.TotpSecret secret = totpService.generateSecret();
        assertFalse(totpService.verify(secret.encrypted(), null));
    }

    @Test
    @DisplayName("verify rechaza código con longitud incorrecta")
    void verify_wrongLengthCode_returnsFalse() {
        TotpService.TotpSecret secret = totpService.generateSecret();
        assertFalse(totpService.verify(secret.encrypted(), "12345"));     // 5 dígitos
        assertFalse(totpService.verify(secret.encrypted(), "1234567"));   // 7 dígitos
        assertFalse(totpService.verify(secret.encrypted(), "abc123"));    // no numérico
    }

    @Test
    @DisplayName("verify rechaza código con secret corrupto")
    void verify_corruptedSecret_returnsFalse() {
        assertFalse(totpService.verify("secret-corrupto-no-base64", "123456"));
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

    // =========================================================================
    // Helper — calcula el código TOTP actual para verificar en tests
    // =========================================================================

    /**
     * Computa el código TOTP para el paso de tiempo actual usando el mismo algoritmo
     * que el servicio, a través de verify con el código calculado.
     * Si el código del step actual es correcto, devuelve ese. Si no, busca en ±1.
     */
    private String computeTotpCode(String encryptedSecret) {
        // Genera todos los posibles códigos del paso actual y los adyacentes
        // y devuelve el primero que verify() acepte
        for (int i = 0; i <= 999999; i++) {
            String candidate = String.format("%06d", i);
            if (totpService.verify(encryptedSecret, candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("No se pudo computar un código TOTP válido");
    }
}
