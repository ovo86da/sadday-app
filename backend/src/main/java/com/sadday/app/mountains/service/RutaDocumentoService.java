package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.RutaDocumentoResponse;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.entity.RutaDocumento;
import com.sadday.app.mountains.entity.RutaDocumentoConfirmacion;
import com.sadday.app.mountains.entity.RutaDocumentoConfirmacionId;
import com.sadday.app.mountains.entity.TipoDocumentoRuta;
import com.sadday.app.mountains.repository.RutaDocumentoConfirmacionRepository;
import com.sadday.app.mountains.repository.RutaDocumentoRepository;
import com.sadday.app.mountains.repository.RutaRepository;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.entity.Documento;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.pdf.DocumentoService;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Documentos adjuntos a una ruta: la plantilla del permiso, el PDF del mapa y
 * los tracks GPX.
 *
 * <p>La subida está abierta a cualquier socio autenticado (FR-022): quien tiene
 * el track es quien caminó la ruta, y restringirlo a la directiva garantizaba
 * que no se subiera nunca. Lo que evita el desorden no es el permiso sino la
 * traza — cada fichero registra quién lo subió— y el contador de
 * confirmaciones, que distingue el track avalado por ocho personas del que no
 * tiene ninguna.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RutaDocumentoService {

    private static final Set<String> ROLES_PRIVILEGIADOS =
            Set.of("ROLE_ADMIN", "ROLE_SECRETARIA", "ROLE_DIRECTIVO");

    /**
     * Cuántos documentos de cada tipo puede tener un mismo socio en una ruta.
     *
     * <p>El track es uno solo: es «mi recorrido», y varios de la misma persona
     * no aportan nada que no diga la fecha. Mapas y plantillas admiten tres,
     * porque puede haber un mapa general y otro de detalle, o versiones de la
     * plantilla de distintos años.
     *
     * <p>El límite es por socio, no por ruta: la gracia del contador de votos
     * es poder comparar el track de uno con el de otro.
     */
    private static final Map<TipoDocumentoRuta, Integer> LIMITE_POR_SOCIO = Map.of(
            TipoDocumentoRuta.TRACK_GPX, 1,
            TipoDocumentoRuta.MAPA,      3,
            TipoDocumentoRuta.PERMISO,   3,
            TipoDocumentoRuta.OTRO,      3);

    /** Carpeta en el storage según el tipo, para que el bucket sea navegable. */
    private static final Map<TipoDocumentoRuta, String> CARPETA = Map.of(
            TipoDocumentoRuta.PERMISO,   "permisos",
            TipoDocumentoRuta.MAPA,      "mapas",
            TipoDocumentoRuta.TRACK_GPX, "tracks",
            TipoDocumentoRuta.OTRO,      "otros");

    private final RutaDocumentoRepository             rutaDocumentoRepository;
    private final RutaDocumentoConfirmacionRepository confirmacionRepository;
    private final RutaRepository                      rutaRepository;
    private final SocioRepository                     socioRepository;
    private final DocumentoService                    documentoService;

    // =========================================================================
    // Subida
    // =========================================================================

    /**
     * Sube un documento a una ruta.
     *
     * @param reemplazarId documento propio al que sustituye, o {@code null} para
     *                     añadir uno nuevo. Se exige explícito: antes el track se
     *                     reemplazaba solo, y eso borraba en silencio el fichero
     *                     anterior sin que la persona se enterara.
     */
    @PreAuthorize("isAuthenticated()")
    public RutaDocumentoResponse subir(Integer rutaId, MultipartFile file,
                                       TipoDocumentoRuta tipo, UUID subidoPorId,
                                       UUID reemplazarId) {
        Ruta  ruta      = findRuta(rutaId);
        Socio subidoPor = findSocio(subidoPorId);

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "documento";

        // Un documento marcado como track tiene que ser un .gpx: el aviso al
        // descargar y las apps recomendadas dependen de que lo sea.
        if (tipo == TipoDocumentoRuta.TRACK_GPX && !original.toLowerCase().endsWith(".gpx")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                    "Un track debe ser un fichero .gpx");
        }

        List<RutaDocumento> propios = rutaDocumentoRepository
                .findByRutaIdAndTipoAndSubidoPorId(rutaId, tipo, subidoPorId);

        if (reemplazarId != null) {
            RutaDocumento aReemplazar = propios.stream()
                    .filter(d -> d.getId().equals(reemplazarId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED,
                            "Solo puedes reemplazar un documento tuyo del mismo tipo"));
            borrar(aReemplazar, "reemplazado");
        } else if (propios.size() >= LIMITE_POR_SOCIO.getOrDefault(tipo, 3)) {
            int limite = LIMITE_POR_SOCIO.getOrDefault(tipo, 3);
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, limite == 1
                    ? "Solo puedes tener un documento de este tipo en la ruta. Reemplaza el que ya subiste."
                    : "Ya tienes " + limite + " documentos de este tipo en la ruta. Reemplaza uno o elimínalo.");
        }

        String objectKey = "rutas/" + rutaId + "/" + CARPETA.get(tipo) + "/"
                + UUID.randomUUID() + "-" + sanitize(original);

        Documento documento = documentoService.guardarArchivo(file, objectKey);

        RutaDocumento rd = RutaDocumento.builder()
                .ruta(ruta)
                .tipo(tipo)
                .documento(documento)
                .subidoPor(subidoPor)
                .build();

        RutaDocumento guardado = rutaDocumentoRepository.save(rd);
        log.info("Documento de ruta subido: rutaId={}, tipo={}, docId={}, por={}, file={}",
                rutaId, tipo, guardado.getId(), subidoPorId, original);
        return toResponse(guardado, 0L, false, true);
    }

    /**
     * Borra un documento y su fichero del storage.
     *
     * <p>Las confirmaciones se van con él por el {@code ON DELETE CASCADE}:
     * avalaban <em>ese</em> fichero, no a la persona, y nadie ha visto todavía
     * el que lo sustituye.
     */
    private void borrar(RutaDocumento doc, String motivo) {
        String objectKey = doc.getDocumento().getObjectKey();
        rutaDocumentoRepository.delete(doc);
        documentoService.eliminarDelStorage(objectKey);
        log.info("Documento {}: docId={}, tipo={}", motivo, doc.getId(), doc.getTipo());
    }

    // =========================================================================
    // Borrado
    // =========================================================================

    /**
     * Borra un documento. Cada socio puede borrar lo suyo; la directiva,
     * cualquiera.
     *
     * <p>La autorización no cabe en un {@code @PreAuthorize} porque depende de
     * quién subió el fichero, que hay que leer de la base primero.
     */
    @PreAuthorize("isAuthenticated()")
    public void eliminar(Integer rutaId, UUID docId, UUID currentUserId) {
        RutaDocumento rd = rutaDocumentoRepository.findByIdAndRutaId(docId, rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND));

        boolean esAutor = rd.getSubidoPor() != null
                && rd.getSubidoPor().getId().equals(currentUserId);

        if (!esAutor && !tieneRolPrivilegiado()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Solo quien subió el documento o la directiva pueden eliminarlo");
        }

        String objectKey = rd.getDocumento().getObjectKey();
        rutaDocumentoRepository.delete(rd);  // cascade elimina el Documento
        documentoService.eliminarDelStorage(objectKey);

        log.info("Documento de ruta eliminado: rutaId={}, docId={}, por={}", rutaId, docId, currentUserId);
    }

    // =========================================================================
    // Confirmaciones
    // =========================================================================

    /** Avala un documento. No se puede avalar lo propio ni confirmar dos veces. */
    @PreAuthorize("isAuthenticated()")
    public long confirmar(Integer rutaId, UUID docId, UUID socioId) {
        RutaDocumento rd = rutaDocumentoRepository.findByIdAndRutaId(docId, rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND));

        if (rd.getSubidoPor() != null && rd.getSubidoPor().getId().equals(socioId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No puedes confirmar un documento que subiste tú");
        }

        RutaDocumentoConfirmacionId id = new RutaDocumentoConfirmacionId(docId, socioId);
        if (confirmacionRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Ya confirmaste este documento");
        }

        confirmacionRepository.save(RutaDocumentoConfirmacion.builder()
                .id(id)
                .rutaDocumento(rd)
                .socio(findSocio(socioId))
                .build());

        long total = confirmacionRepository.countByIdRutaDocumentoId(docId);
        log.info("Documento confirmado: docId={}, por={}, total={}", docId, socioId, total);
        return total;
    }

    /** Retira la confirmación propia: alguien puede descubrir después que el track estaba mal. */
    @PreAuthorize("isAuthenticated()")
    public long retirarConfirmacion(Integer rutaId, UUID docId, UUID socioId) {
        rutaDocumentoRepository.findByIdAndRutaId(docId, rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND));

        RutaDocumentoConfirmacionId id = new RutaDocumentoConfirmacionId(docId, socioId);
        if (!confirmacionRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND,
                    "No habías confirmado este documento");
        }
        confirmacionRepository.deleteById(id);

        long total = confirmacionRepository.countByIdRutaDocumentoId(docId);
        log.info("Confirmación retirada: docId={}, por={}, total={}", docId, socioId, total);
        return total;
    }

    // =========================================================================
    // Lectura
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<RutaDocumentoResponse> listar(Integer rutaId) {
        return listarPorRuta(rutaId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public DescargaDocumento descargar(Integer rutaId, UUID docId) {
        RutaDocumento rd = rutaDocumentoRepository.findByIdAndRutaId(docId, rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND));

        Documento doc   = rd.getDocumento();
        byte[]    bytes = documentoService.descargar(doc);
        return new DescargaDocumento(bytes, doc.getFilename(), doc.getContentType());
    }

    // =========================================================================
    // Uso interno desde otros servicios (sin chequeo de autorización)
    // =========================================================================

    /**
     * Documentos de una ruta, con su número de confirmaciones.
     *
     * <p>Los contadores se traen en una sola consulta agrupada y no uno por
     * documento: la ficha de una ruta puede traer varios tracks.
     *
     * <p>Los campos que dependen de quién consulta —{@code confirmadoPorMi} y
     * {@code puedoEliminar}— se resuelven leyendo el socio del contexto de
     * seguridad, no pasándolo por parámetro, para no tener que propagarlo por
     * los tres servicios que llaman aquí. Si no hay socio en el contexto —por
     * ejemplo una API key del MCP— salen en false, que es lo correcto: esa
     * sesión no puede confirmar ni borrar nada.
     */
    @Transactional(readOnly = true)
    public List<RutaDocumentoResponse> listarPorRuta(Integer rutaId) {
        Map<UUID, Long> conteos = new HashMap<>();
        for (Object[] fila : confirmacionRepository.contarPorRuta(rutaId)) {
            conteos.put((UUID) fila[0], (Long) fila[1]);
        }

        UUID socioId = currentSocioId();
        Set<UUID> mios = socioId == null
                ? Set.of()
                : Set.copyOf(confirmacionRepository.confirmadosPorSocioEnRuta(rutaId, socioId));
        boolean privilegiado = tieneRolPrivilegiado();

        return rutaDocumentoRepository.findByRutaIdOrderByCreatedAtAsc(rutaId)
                .stream()
                .map(rd -> toResponse(
                        rd,
                        conteos.getOrDefault(rd.getId(), 0L),
                        mios.contains(rd.getId()),
                        privilegiado || (socioId != null
                                && rd.getSubidoPor() != null
                                && rd.getSubidoPor().getId().equals(socioId))))
                .toList();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RutaDocumentoResponse toResponse(RutaDocumento rd, long confirmaciones,
                                             boolean confirmadoPorMi, boolean puedoEliminar) {
        Documento doc = rd.getDocumento();
        Socio     por = rd.getSubidoPor();
        return new RutaDocumentoResponse(
                rd.getId(),
                rd.getTipo(),
                doc.getFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                por != null ? por.getId() : null,
                por != null ? por.getNombre() + " " + por.getApellido() : null,
                rd.getCreatedAt(),
                confirmaciones,
                confirmadoPorMi,
                puedoEliminar
        );
    }

    private UUID currentSocioId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof SaddayAuthDetails detalles)) {
            return null;
        }
        return detalles.socioId();
    }

    private boolean tieneRolPrivilegiado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_PRIVILEGIADOS::contains);
    }

    private Ruta findRuta(Integer id) {
        return rutaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_NOT_FOUND));
    }

    private Socio findSocio(UUID id) {
        return socioRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record DescargaDocumento(byte[] bytes, String filename, String contentType) {}
}
