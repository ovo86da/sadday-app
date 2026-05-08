package com.sadday.app.auth.service;

import com.sadday.app.config.SecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Servicio TOTP (RFC 6238) para autenticación de dos factores.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Generar secrets TOTP aleatorios (20 bytes, devueltos en Base32 para el QR)</li>
 *   <li>Cifrar/descifrar el secret en reposo con AES-256-GCM</li>
 *   <li>Verificar códigos TOTP de 6 dígitos con ventana de ±1 paso (±30 segundos)</li>
 *   <li>Construir la URI otpauth:// para generar el QR code</li>
 * </ul>
 *
 * <p>Seguridad:
 * <ul>
 *   <li>Secret almacenado en BD = Base64(IV || ciphertext || GCM-tag), IV de 12 bytes aleatorios.</li>
 *   <li>La clave AES-256 se carga de la variable de entorno TOTP_ENCRYPTION_KEY al arranque.</li>
 *   <li>Nunca se loguea ni se devuelve el secret cifrado fuera del flujo de setup.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

    private static final String AES_GCM         = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LENGTH   = 12;   // bytes
    private static final int    GCM_TAG_BITS    = 128;
    private static final int    TOTP_STEP_SECS  = 30;
    private static final int    TOTP_DIGITS     = 6;
    private static final int    TOTP_WINDOW     = 1;    // pasos de tolerancia (±30s)

    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKeySpec aesKey;

    @PostConstruct
    private void initKey() {
        byte[] keyBytes = Base64.getDecoder().decode(securityProperties.getTotpEncryptionKey());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "TOTP encryption key must be exactly 32 bytes (256 bits) base64-encoded. " +
                    "Generate with: openssl rand -base64 32");
        }
        aesKey = new SecretKeySpec(keyBytes, "AES");
        log.info("TotpService inicializado con clave AES-256-GCM");
    }

    /**
     * Genera un nuevo secret TOTP aleatorio.
     *
     * @return {@link TotpSecret} con el secret cifrado (para guardar en BD)
     *         y en Base32 (para mostrar al usuario en el QR).
     */
    public TotpSecret generateSecret() {
        byte[] rawSecret = new byte[20];
        secureRandom.nextBytes(rawSecret);
        return new TotpSecret(encrypt(rawSecret), toBase32(rawSecret));
    }

    /**
     * Verifica un código TOTP de 6 dígitos.
     *
     * @param encryptedSecret secret cifrado almacenado en BD
     * @param code            código de 6 dígitos introducido por el usuario
     * @return true si el código es válido en la ventana temporal actual
     */
    public boolean verify(String encryptedSecret, String code) {
        if (encryptedSecret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        try {
            int providedCode = Integer.parseInt(code);
            byte[] rawSecret = decrypt(encryptedSecret);
            long counter = System.currentTimeMillis() / 1000L / TOTP_STEP_SECS;

            for (long step = counter - TOTP_WINDOW; step <= counter + TOTP_WINDOW; step++) {
                if (computeTotp(rawSecret, step) == providedCode) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Error verificando código TOTP: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Construye la URI otpauth:// para generar el QR code con apps como Google Authenticator.
     *
     * @param username     nombre de usuario del socio
     * @param base32Secret secret en Base32 (el devuelto por {@link #generateSecret()})
     */
    public String buildOtpAuthUri(String username, String base32Secret) {
        return String.format(
                "otpauth://totp/Sadday:%s?secret=%s&issuer=Sadday&digits=6&period=30",
                username, base32Secret);
    }

    // =========================================================================
    // Implementación interna
    // =========================================================================

    /** RFC 6238 TOTP = HOTP(key, floor(time/30)) truncado a 6 dígitos. */
    private int computeTotp(byte[] secret, long counter) throws Exception {
        byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] hash = mac.doFinal(msg);

        int offset = hash[hash.length - 1] & 0x0F;
        int code = ((hash[offset]     & 0x7F) << 24)
                 | ((hash[offset + 1] & 0xFF) << 16)
                 | ((hash[offset + 2] & 0xFF) << 8)
                 |  (hash[offset + 3] & 0xFF);

        return code % (int) Math.pow(10, TOTP_DIGITS);
    }

    private String encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Formato almacenado: Base64(iv || ciphertext_with_tag)
            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,             GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Error cifrando TOTP secret", e);
        }
    }

    private byte[] decrypt(String encryptedBase64) {
        try {
            byte[] combined   = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv         = Arrays.copyOfRange(combined, 0,             GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Error descifrando TOTP secret", e);
        }
    }

    /** Codificación Base32 sin padding según RFC 4648 (compatible con apps TOTP). */
    private String toBase32(byte[] input) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : input) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return sb.toString();
    }

    // =========================================================================

    /**
     * Par (encryptedSecret, base32Secret) devuelto por {@link #generateSecret()}.
     *
     * @param encrypted secret cifrado con AES-256-GCM — guardar en BD
     * @param base32    secret en Base32 — mostrar al usuario para el QR
     */
    public record TotpSecret(String encrypted, String base32) {}
}
