package com.sadday.app.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtService — Unit Tests")
class JwtServiceTest {

    private static JwtService    jwtService;
    private static JwtDecoder    jwtDecoder;
    private static RSAPublicKey  rsaPublicKey;
    private static RSAPrivateKey rsaPrivateKey;

    private static final String ISSUER   = "sadday-test";
    private static final String AUDIENCE = "sadday-api-test";

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        rsaPublicKey  = (RSAPublicKey)  kp.getPublic();
        rsaPrivateKey = (RSAPrivateKey) kp.getPrivate();

        JwtProperties props = new JwtProperties();
        props.setIssuer(ISSUER);
        props.setAudience(AUDIENCE);
        props.setAccessTokenExpirationSeconds(300);

        RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey).privateKey(rsaPrivateKey).build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

        jwtDecoder = buildDecoder(rsaPublicKey, ISSUER, AUDIENCE);
        jwtService = new JwtService(encoder, jwtDecoder, props);
    }

    // =========================================================================
    // generateAccessToken — claims
    // =========================================================================

    @Nested
    @DisplayName("generateAccessToken — claims")
    class GenerateAccessToken {

        @Test
        @DisplayName("El token emitido contiene el claim aud con el valor configurado")
        void token_containsAudienceClaim() {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), "juan", "SOCIO", "Juan");
            var jwt = jwtDecoder.decode(token);

            assertNotNull(jwt.getAudience());
            assertTrue(jwt.getAudience().contains(AUDIENCE));
        }

        @Test
        @DisplayName("El token emitido contiene iss, sub, socio_id y rol correctos")
        void token_containsStandardClaims() {
            UUID socioId = UUID.randomUUID();
            String token = jwtService.generateAccessToken(socioId, "juan", "ADMIN", "Juan");
            var jwt = jwtDecoder.decode(token);

            assertEquals(ISSUER,             jwt.getClaimAsString(JwtClaimNames.ISS));
            assertEquals("juan",             jwt.getSubject());
            assertEquals(socioId.toString(), jwt.getClaimAsString("socio_id"));
            assertEquals("ADMIN",            jwt.getClaimAsString("rol"));
        }
    }

    // =========================================================================
    // jwtDecoder — validación de audience
    // =========================================================================

    @Nested
    @DisplayName("jwtDecoder — validación de audience")
    class AudienceValidation {

        @Test
        @DisplayName("Token con aud correcto es aceptado")
        void decoder_acceptsTokenWithCorrectAudience() {
            String token = jwtService.generateAccessToken(UUID.randomUUID(), "juan", "SOCIO", "Juan");
            assertDoesNotThrow(() -> jwtDecoder.decode(token));
        }

        @Test
        @DisplayName("Token con aud incorrecto es rechazado")
        void decoder_rejectsTokenWithWrongAudience() throws Exception {
            // Encoder que emite aud="otro-servicio" con las mismas claves RSA
            JwtProperties wrongProps = new JwtProperties();
            wrongProps.setIssuer(ISSUER);
            wrongProps.setAudience("otro-servicio");
            wrongProps.setAccessTokenExpirationSeconds(300);

            RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey).privateKey(rsaPrivateKey).build();
            JwtEncoder wrongEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
            JwtDecoder permissiveDecoder = NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
            JwtService wrongService = new JwtService(wrongEncoder, permissiveDecoder, wrongProps);

            String tokenWrongAud = wrongService.generateAccessToken(
                    UUID.randomUUID(), "juan", "SOCIO", "Juan");

            // El decoder real (con validación de aud) debe rechazarlo
            assertThrows(JwtException.class, () -> jwtDecoder.decode(tokenWrongAud));
        }

        @Test
        @DisplayName("Token sin claim aud es rechazado")
        void decoder_rejectsTokenWithoutAudience() throws Exception {
            // Emitimos un token sin aud usando JwtClaimsSet directamente
            JwtClaimsSet claimsWithoutAud = JwtClaimsSet.builder()
                    .issuer(ISSUER)
                    .issuedAt(java.time.Instant.now())
                    .expiresAt(java.time.Instant.now().plusSeconds(300))
                    .subject("juan")
                    .build();

            RSAKey rsaKey = new RSAKey.Builder(rsaPublicKey).privateKey(rsaPrivateKey).build();
            JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
            String tokenNoAud = encoder.encode(
                    JwtEncoderParameters.from(
                            JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(),
                            claimsWithoutAud))
                    .getTokenValue();

            assertThrows(JwtException.class, () -> jwtDecoder.decode(tokenNoAud));
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static NimbusJwtDecoder buildDecoder(RSAPublicKey publicKey, String issuer, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(audience)
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator
        ));
        return decoder;
    }
}
