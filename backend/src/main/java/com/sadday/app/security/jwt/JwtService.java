package com.sadday.app.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Servicio para generación y validación de access tokens JWT (RS256).
 *
 * <p>El refresh token es un UUID opaco generado externamente y
 * almacenado como hash SHA-256 en la base de datos. Este servicio
 * solo gestiona los access tokens JWT.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder  jwtEncoder;
    private final JwtDecoder  jwtDecoder;
    private final JwtProperties jwtProperties;

    /**
     * Genera un access token JWT firmado con RS256.
     *
     * <p>Claims incluidos: sub (username), socio_id, rol, nombre.
     * NO se incluyen datos sensibles (cédula, email, etc.).
     */
    public String generateAccessToken(UUID socioId, String username, String rol, String nombre) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpirationSeconds());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(username)
                .claim("socio_id", socioId.toString())
                .claim("rol", rol)
                .claim("nombre", nombre)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Extrae el username (subject) del token.
     * Lanza excepción si el token es inválido o está expirado.
     */
    public String extractUsername(String token) {
        return decode(token).getSubject();
    }

    /** Extrae el socio_id del token como UUID. */
    public UUID extractSocioId(String token) {
        String id = decode(token).getClaimAsString("socio_id");
        return UUID.fromString(id);
    }

    /** Extrae el rol del token. */
    public String extractRol(String token) {
        return decode(token).getClaimAsString("rol");
    }

    /**
     * Verifica si el token es estructuralmente válido y no ha expirado.
     * La firma ya es verificada por el JwtDecoder internamente.
     */
    public boolean isTokenValid(String token) {
        try {
            Jwt jwt = decode(token);
            Instant expiry = jwt.getExpiresAt();
            return expiry != null && expiry.isAfter(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    private Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }
}
