package com.sadday.app.auth.service;

import com.sadday.app.auth.dto.CompleteRegistroRequest;
import com.sadday.app.auth.entity.EmailVerificationToken;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.EmailVerificationTokenRepository;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.config.AuthProperties;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.auth.dto.TokenInfoResponse;
import com.sadday.app.socios.dto.InvitacionPendienteResponse;
import com.sadday.app.socios.entity.*;
import com.sadday.app.socios.repository.*;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Gestiona el flujo de registro inicial de un socio.
 *
 * <p><b>Flujo nuevo (pre-registro):</b>
 * <ol>
 *   <li>La Secretaria proporciona cédula + correo + teléfono.</li>
 *   <li>Se llama a {@link #sendMinimalInvitation} — se guarda el token con esos datos
 *       (sin crear el registro en {@code socios} todavía).</li>
 *   <li>El socio recibe el link, lo abre y envía sus datos personales + credenciales.</li>
 *   <li>{@link #complete} detecta que {@code socioId == null} en el token, crea el {@code Socio}
 *       con los datos del formulario y luego crea {@code UsuarioAuth}.</li>
 * </ol>
 *
 * <p><b>Flujo legacy (reenvío a socio ya existente):</b>
 * <ol>
 *   <li>El socio ya existe en {@code socios} pero no tiene credenciales.</li>
 *   <li>Se llama a {@link #sendInvitation(UUID, String)} — token ligado al {@code socioId}.</li>
 *   <li>{@link #complete} detecta {@code socioId != null} y solo crea {@code UsuarioAuth}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UsuarioAuthRepository            usuarioAuthRepository;
    private final JavaMailSender                   mailSender;
    private final PasswordEncoder                  passwordEncoder;
    private final AuthProperties                   authProperties;

    // Repositorios de socios (para crear el registro en el flujo nuevo)
    private final SocioRepository              socioRepository;
    private final EstadoHabilitacionRepository estadoHabRepo;
    private final TipoSocioClubRepository      tipoSocioRepo;
    private final RolSistemaRepository         rolSistemaRepo;
    private final com.sadday.app.socios.repository.EstadoAccesoRepository estadoAccesoRepo;
    private final ClasificacionSocioRepository clasifSocioRepo;

    private final SecureRandom secureRandom = new SecureRandom();

    private static final String ESTADO_HABILITADO   = "Habilitado";
    private static final String TIPO_ASPIRANTE      = "Aspirante";
    private static final String ROL_SOCIO           = "Socio";
    private static final String ACCESO_ACTIVE       = "ACTIVE";

    @Value("${sadday.mail.from}")
    private String mailFrom;

    @Value("${sadday.mail.app-url}")
    private String appUrl;

    // =========================================================================
    // Flujo nuevo: pre-registro (socio aún no existe en BD)
    // =========================================================================

    /**
     * Genera una invitación de pre-registro con los datos mínimos.
     * El socio completará el resto de sus datos personales al abrir el link.
     *
     * @param cedula   cédula del candidato (para validar unicidad)
     * @param correo   correo al que se envía la invitación
     * @param telefono teléfono opcional del candidato
     */
    public void sendMinimalInvitation(String cedula, String correo, String telefono) {
        // Verificar que cédula y correo no estén ya registrados
        if (socioRepository.existsByCedula(cedula)) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                    "Ya existe un socio registrado con esa cédula.");
        }
        if (socioRepository.existsByCorreo(correo)) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                    "Ya existe un socio registrado con ese correo.");
        }

        // Invalidar invitaciones de pre-registro anteriores para el mismo correo
        tokenRepository.invalidateAllByCorreo(correo);

        String rawToken  = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .cedula(cedula)
                .correo(correo)
                .telefono(telefono)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now()
                        .plusHours(authProperties.getEmailVerificationTokenExpiryHours()))
                .build();
        tokenRepository.save(token);

        sendInvitationEmail(correo, rawToken);
        log.info("Invitación de pre-registro enviada a correo={}", correo);
    }

    // =========================================================================
    // Flujo legacy: reenvío a socio ya existente en BD
    // =========================================================================

    /**
     * Genera un token de invitación y envía el email a un socio ya creado en BD.
     * Usado únicamente para reenviar la invitación a socios que aún no activaron su cuenta.
     */
    public void sendInvitation(UUID socioId, String correo) {
        tokenRepository.invalidateAllBySocioId(socioId);

        String rawToken  = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .socioId(socioId)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now()
                        .plusHours(authProperties.getEmailVerificationTokenExpiryHours()))
                .build();
        tokenRepository.save(token);

        sendInvitationEmail(correo, rawToken);
        log.info("Invitación (legacy) enviada a socio_id={}", socioId);
    }

    // =========================================================================
    // Flujo CSV import: pre-registro con datos pre-cargados
    // =========================================================================

    /**
     * Genera una invitación con datos pre-cargados desde un CSV.
     * El socio solo completa los datos faltantes (fecha nacimiento, dirección,
     * contacto de emergencia) y sus credenciales.
     */
    public void sendCsvImportInvitation(String cedula, String correo, String telefono,
                                        String nombre, String apellido,
                                        String tipoSocioNombre, String nivelTecnicoNombre) {
        if (socioRepository.existsByCedula(cedula)) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                    "Ya existe un socio con esa cédula: " + cedula);
        }
        if (socioRepository.existsByCorreo(correo)) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                    "Ya existe un socio con ese correo: " + correo);
        }

        tokenRepository.invalidateAllByCorreo(correo);

        String rawToken  = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .cedula(cedula)
                .correo(correo)
                .telefono(telefono)
                .nombre(nombre)
                .apellido(apellido)
                .tipoSocioNombre(tipoSocioNombre)
                .nivelTecnicoNombre(nivelTecnicoNombre)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now()
                        .plusHours(authProperties.getEmailVerificationTokenExpiryHours()))
                .build();
        tokenRepository.save(token);

        sendInvitationEmail(correo, rawToken);
        log.info("Invitación CSV enviada: cedula={}, correo={}", cedula, correo);
    }

    // =========================================================================
    // Invitaciones pendientes (vista para Secretaria / Admin)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<InvitacionPendienteResponse> listarInvitacionesPendientes() {
        return tokenRepository.findBySocioIdIsNullAndUsedFalseOrderByCreatedAtDesc()
                .stream()
                .map(t -> new InvitacionPendienteResponse(
                        t.getId(),
                        t.getCedula(),
                        t.getCorreo(),
                        t.getTelefono(),
                        t.getNombre(),
                        t.getApellido(),
                        t.isFromCsvImport(),
                        t.getCreatedAt(),
                        t.getExpiresAt(),
                        t.isExpired() ? "EXPIRADO" : "PENDIENTE"
                ))
                .toList();
    }

    public void eliminarInvitacion(UUID tokenId) {
        EmailVerificationToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID,
                        "Invitación no encontrada."));

        if (token.getSocioId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Esta invitación corresponde a un socio ya registrado.");
        }
        if (token.isUsed()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Esta invitación ya fue aceptada. El socio completó su registro.");
        }

        tokenRepository.deleteById(tokenId);
        log.info("Invitación eliminada manualmente: tokenId={}, correo={}", tokenId, token.getCorreo());
    }

    public void reenviarInvitacionPreRegistro(UUID tokenId) {
        EmailVerificationToken old = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID,
                        "Invitación no encontrada."));

        if (old.getSocioId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Esta invitación corresponde a un socio existente. Use el reenvío estándar.");
        }
        if (old.isUsed()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Esta invitación ya fue aceptada. El socio completó su registro.");
        }

        tokenRepository.invalidateAllByCorreo(old.getCorreo());

        String rawToken  = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken nuevo = EmailVerificationToken.builder()
                .cedula(old.getCedula())
                .correo(old.getCorreo())
                .telefono(old.getTelefono())
                .nombre(old.getNombre())
                .apellido(old.getApellido())
                .tipoSocioNombre(old.getTipoSocioNombre())
                .nivelTecnicoNombre(old.getNivelTecnicoNombre())
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now()
                        .plusHours(authProperties.getEmailVerificationTokenExpiryHours()))
                .build();
        tokenRepository.save(nuevo);

        sendInvitationEmail(old.getCorreo(), rawToken);
        log.info("Invitación de pre-registro reenviada: tokenId={}, correo={}", tokenId, old.getCorreo());
    }

    // =========================================================================
    // Consultar tipo de token (público — para que el frontend adapte el formulario)
    // =========================================================================

    /**
     * Verifica que el token sea válido y devuelve la información necesaria para que
     * el frontend adapte el formulario de registro.
     */
    @Transactional(readOnly = true)
    public TokenInfoResponse getTokenInfo(String rawToken) {
        String tokenHash = hashToken(rawToken);
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
        if (!token.isValid()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        boolean requiresPersonalData = token.getSocioId() == null;
        boolean fromCsvImport        = token.isFromCsvImport();
        return new TokenInfoResponse(
                requiresPersonalData,
                fromCsvImport,
                token.getNombre(),
                token.getApellido(),
                token.getTipoSocioNombre(),
                token.getNivelTecnicoNombre()
        );
    }

    // =========================================================================
    // Completar registro
    // =========================================================================

    /**
     * Completa el registro.
     *
     * <ul>
     *   <li>Si el token tiene {@code socioId} → flujo legacy: solo crea {@code UsuarioAuth}.</li>
     *   <li>Si el token no tiene {@code socioId} → flujo nuevo: crea {@code Socio} + {@code UsuarioAuth}.</li>
     * </ul>
     */
    public void complete(CompleteRegistroRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Las contraseñas no coinciden");
        }

        String tokenHash = hashToken(request.token());
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        if (!token.isValid()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        if (usuarioAuthRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                    "El nombre de usuario ya está en uso. Elige otro.");
        }

        UUID socioId;

        if (token.getSocioId() != null) {
            // ── Flujo legacy: socio ya existe ──────────────────────────────
            socioId = token.getSocioId();

            if (usuarioAuthRepository.findBySocioId(socioId).isPresent()) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID,
                        "Este socio ya completó su registro.");
            }

        } else {
            // ── Flujo nuevo: crear el socio con los datos del formulario ───
            if (token.isFromCsvImport()) {
                validarDatosCsvCompletion(request);
            } else {
                validarDatosPersonales(request);
            }

            // Doble-verificar unicidad (la solicitud pudo llegar con datos modificados)
            if (socioRepository.existsByCedula(token.getCedula())) {
                throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                        "Ya existe un socio con esa cédula.");
            }
            if (socioRepository.existsByCorreo(token.getCorreo())) {
                throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                        "Ya existe un socio con ese correo.");
            }

            // Nombre y apellido: del token (CSV) o del formulario (flujo manual)
            String nombre   = token.getNombre()   != null ? token.getNombre()   : request.nombre().trim();
            String apellido = token.getApellido() != null ? token.getApellido() : request.apellido().trim();

            EstadoHabilitacion estado = estadoHabRepo.findByNombre(ESTADO_HABILITADO)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Estado '" + ESTADO_HABILITADO + "' no encontrado"));

            // Tipo de socio: del token (CSV) o default Aspirante
            TipoSocioClub tipo = (token.getTipoSocioNombre() != null)
                    ? tipoSocioRepo.findByNombre(token.getTipoSocioNombre())
                            .orElseGet(() -> tipoSocioRepo.findByNombre(TIPO_ASPIRANTE)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                            "Tipo '" + TIPO_ASPIRANTE + "' no encontrado")))
                    : tipoSocioRepo.findByNombre(TIPO_ASPIRANTE)
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                    "Tipo '" + TIPO_ASPIRANTE + "' no encontrado"));

            // Nivel técnico: del token (CSV) o null
            ClasificacionSocio nivelTecnico = (token.getNivelTecnicoNombre() != null)
                    ? clasifSocioRepo.findByNombreIgnoreCase(token.getNivelTecnicoNombre()).orElse(null)
                    : null;

            com.sadday.app.socios.entity.EstadoAcceso acceso = estadoAccesoRepo.findByCodigo(ACCESO_ACTIVE)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Estado de acceso '" + ACCESO_ACTIVE + "' no encontrado"));
            RolSistema rol = rolSistemaRepo.findByNombre(ROL_SOCIO)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Rol '" + ROL_SOCIO + "' no encontrado"));

            Socio socio = Socio.builder()
                    .nombre(nombre)
                    .apellido(apellido)
                    .cedula(token.getCedula())
                    .correo(token.getCorreo())
                    .telefono(token.getTelefono())
                    .fechaNacimiento(request.fechaNacimiento())
                    .tipoSangre(request.tipoSangre())
                    .direccion(request.direccion())
                    .emergencyContactName(request.emergencyContactName())
                    .emergencyContactPhone(request.emergencyContactPhone())
                    .emergencyContactDireccion(request.emergencyContactDireccion())
                    .emergencyContactName2(request.emergencyContactName2())
                    .emergencyContactPhone2(request.emergencyContactPhone2())
                    .emergencyContactDireccion2(request.emergencyContactDireccion2())
                    .estadoHabilitacion(estado)
                    .tipoSocio(tipo)
                    .nivelTecnico(nivelTecnico)
                    .estadoAcceso(acceso)
                    .rolSistema(rol)
                    .build();

            Socio saved = socioRepository.save(socio);
            socioId = saved.getId();
            log.info("Socio creado en completar registro: id={}, cedula={}", socioId, token.getCedula());
        }

        // Crear credenciales
        UsuarioAuth usuario = UsuarioAuth.builder()
                .socioId(socioId)
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        try {
            usuarioAuthRepository.save(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS,
                    "El nombre de usuario ya está en uso. Elige otro.");
        }

        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Registro completado: socioId={}, username={}", socioId, request.username());
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private void validarDatosPersonales(CompleteRegistroRequest req) {
        if (req.nombre() == null || req.nombre().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El nombre es obligatorio.");
        }
        if (req.apellido() == null || req.apellido().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El apellido es obligatorio.");
        }
        if (req.fechaNacimiento() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La fecha de nacimiento es obligatoria.");
        }
        if (!req.fechaNacimiento().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La fecha de nacimiento debe ser en el pasado.");
        }
    }

    private void validarDatosCsvCompletion(CompleteRegistroRequest req) {
        if (req.fechaNacimiento() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La fecha de nacimiento es obligatoria.");
        }
        if (!req.fechaNacimiento().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La fecha de nacimiento debe ser en el pasado.");
        }
        if (req.direccion() == null || req.direccion().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La dirección es obligatoria.");
        }
        if (req.emergencyContactName() == null || req.emergencyContactName().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El nombre del contacto de emergencia es obligatorio.");
        }
        if (req.emergencyContactPhone() == null || req.emergencyContactPhone().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El teléfono del contacto de emergencia es obligatorio.");
        }
    }

    private void sendInvitationEmail(String correo, String rawToken) {
        String registroLink = appUrl + "/registro/completar?token=" + rawToken;
        int expiryHours = authProperties.getEmailVerificationTokenExpiryHours();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(correo);
        message.setSubject("Club Sadday — Invitación para activar tu cuenta");
        message.setText("""
                ¡Bienvenido al Club de Montaña Sadday!

                La Secretaría ha iniciado tu proceso de registro. Para completarlo y
                establecer tus datos personales, usuario y contraseña, haz clic en el
                siguiente enlace:

                %s

                Este enlace es válido por %d horas.

                Si no esperabas este correo, por favor contáctanos.
                """.formatted(registroLink, expiryHours));
        mailSender.send(message);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
