package com.sadday.app.security.jwt;

import java.io.Serializable;
import java.util.UUID;

/**
 * Datos adicionales del usuario autenticado, adjuntos al {@code Authentication}
 * del SecurityContext tras validar el JWT.
 *
 * <p>Permite obtener el {@code socioId} y el rol en cualquier parte
 * de la aplicación sin necesidad de hacer una consulta extra a la BD.
 *
 * <p>Uso en controladores:
 * <pre>
 * {@code
 * SaddayAuthDetails details = (SaddayAuthDetails) authentication.getDetails();
 * UUID socioId = details.socioId();
 * }
 * </pre>
 */
public record SaddayAuthDetails(UUID socioId, String rol) implements Serializable {
}
