package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.RutaDocumentoResponse;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.entity.RutaDocumento;
import com.sadday.app.mountains.repository.RutaDocumentoRepository;
import com.sadday.app.mountains.repository.RutaRepository;
import com.sadday.app.shared.entity.Documento;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.pdf.DocumentoService;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RutaDocumentoService {

    private final RutaDocumentoRepository rutaDocumentoRepository;
    private final RutaRepository          rutaRepository;
    private final SocioRepository         socioRepository;
    private final DocumentoService        documentoService;

    // =========================================================================
    // API pública (con autorización)
    // =========================================================================

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public RutaDocumentoResponse subir(Integer rutaId, MultipartFile file, UUID subidoPorId) {
        Ruta ruta = findRuta(rutaId);
        Socio subidoPor = findSocio(subidoPorId);

        String original  = file.getOriginalFilename() != null ? file.getOriginalFilename() : "documento";
        String objectKey = "rutas/" + rutaId + "/permisos/" + UUID.randomUUID() + "-" + sanitize(original);

        Documento documento = documentoService.guardarArchivo(file, objectKey);

        RutaDocumento rd = RutaDocumento.builder()
                .ruta(ruta)
                .documento(documento)
                .subidoPor(subidoPor)
                .build();

        log.info("Documento de permiso subido: rutaId={}, docId={}, file={}", rutaId, documento.getId(), original);
        return toResponse(rutaDocumentoRepository.save(rd));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public void eliminar(Integer rutaId, UUID docId) {
        RutaDocumento rd = rutaDocumentoRepository.findByIdAndRutaId(docId, rutaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND));

        String objectKey = rd.getDocumento().getObjectKey();
        rutaDocumentoRepository.delete(rd);  // cascade elimina el Documento
        documentoService.eliminarDelStorage(objectKey);

        log.info("Documento de permiso eliminado: rutaId={}, docId={}", rutaId, docId);
    }

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

        Documento doc    = rd.getDocumento();
        byte[]    bytes  = documentoService.descargar(doc);
        return new DescargaDocumento(bytes, doc.getFilename(), doc.getContentType());
    }

    // =========================================================================
    // Uso interno desde otros servicios (sin chequeo de autorización)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<RutaDocumentoResponse> listarPorRuta(Integer rutaId) {
        return rutaDocumentoRepository.findByRutaIdOrderByCreatedAtAsc(rutaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RutaDocumentoResponse toResponse(RutaDocumento rd) {
        Documento doc = rd.getDocumento();
        String subidoPorNombre = rd.getSubidoPor() != null
                ? rd.getSubidoPor().getNombre() + " " + rd.getSubidoPor().getApellido()
                : null;
        return new RutaDocumentoResponse(
                rd.getId(),
                doc.getFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                subidoPorNombre,
                rd.getCreatedAt()
        );
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
