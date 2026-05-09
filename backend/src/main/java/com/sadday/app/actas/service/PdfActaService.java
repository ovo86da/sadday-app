package com.sadday.app.actas.service;

import com.sadday.app.actas.entity.ActaInformeSalida;
import com.sadday.app.actas.entity.ActaReunion;
import com.sadday.app.actas.entity.AsistenteReunion;
import com.sadday.app.actas.entity.TipoActa;
import com.sadday.app.actas.repository.ActaInformeSalidaRepository;
import com.sadday.app.actas.repository.ActaReunionRepository;
import com.sadday.app.actas.repository.AsistenteReunionRepository;
import com.sadday.app.salidas.repository.SalidaParticipanteDignidadRepository;
import com.sadday.app.shared.entity.Documento;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.pdf.DocumentoService;
import com.sadday.app.shared.pdf.PdfRenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Genera y sirve el PDF de un Acta de Reunión.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfActaService {

    private final ActaReunionRepository                  actaRepository;
    private final AsistenteReunionRepository              asistenteRepository;
    private final ActaInformeSalidaRepository             informeLinkRepository;
    private final SalidaParticipanteDignidadRepository    dignidadRepository;
    private final PdfRenderService                        pdfRenderService;
    private final DocumentoService                        documentoService;

    @Lazy
    @Autowired
    private PdfActaService self;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Parser y renderer de CommonMark (thread-safe, se crean una sola vez). */
    private static final Parser       MD_PARSER   = Parser.builder().build();
    private static final HtmlRenderer MD_RENDERER = HtmlRenderer.builder().build();

    /**
     * Convierte texto Markdown a HTML usando CommonMark.
     * Soporta correctamente negritas, cursivas, listas, headings, etc.
     */
    private static String md(String text) {
        if (text == null || text.isBlank()) return null;
        Node document = MD_PARSER.parse(text);
        return MD_RENDERER.render(document).trim();
    }

    /** Genera (o regenera) el PDF del acta, lo sube a S3 y actualiza la FK en BD. */
    @Transactional
    public Documento generarPdf(UUID actaId) {
        ActaReunion acta = actaRepository.findById(actaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTA_NOT_FOUND));

        List<AsistenteReunion> asistentes = asistenteRepository.findByActaId(actaId);
        List<ActaInformeSalida> links     = informeLinkRepository.findByActaId(actaId);

        Map<String, Object> vars = buildVars(acta, asistentes, links);
        byte[] pdf = pdfRenderService.render("acta-reunion", vars);

        String fecha     = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String filename  = "acta-reunion-" + actaId + "-" + fecha + ".pdf";
        String objectKey = "actas/" + actaId + "/" + filename;

        Documento doc = documentoService.guardar(pdf, objectKey, filename);
        acta.setDocumento(doc);
        actaRepository.save(acta);

        return doc;
    }

    /** Descarga los bytes del PDF ya generado. Lanza excepción si no existe. */
    @Transactional(readOnly = true)
    public byte[] descargarPdf(UUID actaId) {
        ActaReunion acta = actaRepository.findById(actaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTA_NOT_FOUND));
        if (acta.getDocumento() == null) {
            throw new BusinessException(ErrorCode.DOCUMENTO_NO_GENERADO);
        }
        // PDFs de actas de directiva: solo ADMIN, SECRETARIA y DIRECTIVO
        if (acta.getTipoActa() == TipoActa.DIRECTIVA && esSocioRegular()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return documentoService.descargar(acta.getDocumento());
    }

    /** True si el usuario no tiene ningún rol privilegiado (es socio regular). */
    private boolean esSocioRegular() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return true;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(a -> a.equals("ROLE_ADMIN")
                        || a.equals("ROLE_SECRETARIA")
                        || a.equals("ROLE_DIRECTIVO"));
    }

    /** Devuelve el filename del documento para el header Content-Disposition. */
    @Transactional(readOnly = true)
    public String getFilename(UUID actaId) {
        return self.getDocumento(actaId)
                .map(Documento::getFilename)
                .orElse("acta-reunion-" + actaId + ".pdf");
    }

    /** Devuelve el documento asociado al acta. Empty si aún no se generó el PDF. */
    @Transactional(readOnly = true)
    public Optional<Documento> getDocumento(UUID actaId) {
        ActaReunion acta = actaRepository.findById(actaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTA_NOT_FOUND));
        if (acta.getDocumento() == null) {
            return Optional.empty();
        }
        Documento doc = acta.getDocumento();
        // Forzar inicialización del proxy antes de que cierre la sesión
        doc.getFilename();
        doc.getChecksumSha256();
        doc.getChecksumMd5();
        doc.getSizeBytes();
        return Optional.of(doc);
    }

    private Map<String, Object> buildVars(ActaReunion acta,
                                          List<AsistenteReunion> asistentes,
                                          List<ActaInformeSalida> links) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("fecha",   acta.getFecha().format(DATE_FMT));
        vars.put("hora",    acta.getHora().toString());
        vars.put("lugar",   acta.getLugar() != null && !acta.getLugar().isBlank()
                ? acta.getLugar() : "Reunión Virtual");
        vars.put("creadaPorNombre",
                acta.getCreadaPor().getNombre() + " " + acta.getCreadaPor().getApellido());
        String actDesc = md(acta.getActividadesRealizadasDesc());
        log.debug("md(actividadesRealizadasDesc) = {}", actDesc);
        vars.put("actividadesRealizadasDesc", actDesc);
        vars.put("actividadesPorRealizar",    md(acta.getActividadesPorRealizar()));
        vars.put("acuerdos",      md(acta.getAcuerdos()));
        vars.put("varios",        md(acta.getVarios()));
        vars.put("observaciones", md(acta.getObservaciones()));
        var presidente = acta.getPresidenteReunion();
        var secretaria = acta.getSecretariaReunion();
        vars.put("presidenteNombre", presidente != null
                ? presidente.getNombre() + " " + presidente.getApellido() : null);
        vars.put("secretariaNombre", secretaria != null
                ? secretaria.getNombre() + " " + secretaria.getApellido() : null);
        // Asistentes — dos columnas si hay más de 6
        List<String> nombresAsistentes = asistentes.stream()
                .map(a -> a.getSocio() != null
                        ? a.getSocio().getApellido() + ", " + a.getSocio().getNombre()
                        : a.getNombreRaw())
                .toList();
        int mid = (nombresAsistentes.size() + 1) / 2;
        List<String> col1 = nombresAsistentes.subList(0, Math.min(mid, nombresAsistentes.size()));
        List<String> col2 = nombresAsistentes.size() > mid
                ? nombresAsistentes.subList(mid, nombresAsistentes.size())
                : List.of();
        vars.put("asistentes", nombresAsistentes);
        vars.put("asistentesCol1", col1);
        vars.put("asistentesCol2", col2);
        vars.put("asistentesDobleColumna", nombresAsistentes.size() > 6);

        // Informes vinculados — incluye jefe de salida
        vars.put("informesVinculados", links.stream()
                .map(l -> {
                    var salida = l.getInforme().getSalida();
                    var jefes = dignidadRepository
                            .findByParticipante_Salida_IdAndDignidad_Nombre(salida.getId(), "Jefe de Salida");
                    String jefe = jefes.isEmpty() ? "—" : jefes.stream()
                            .map(j -> j.getParticipante().getSocio().getApellido()
                                    + ", " + j.getParticipante().getSocio().getNombre())
                            .findFirst().orElse("—");
                    Map<String, String> m = new HashMap<>();
                    m.put("nombre", salida.getNombre());
                    m.put("fecha", salida.getFechaInicio() != null
                            ? salida.getFechaInicio().format(DATE_FMT) : "—");
                    m.put("jefeSalida", jefe);
                    return m;
                })
                .toList());
        vars.put("esDirectiva", acta.getTipoActa() == TipoActa.DIRECTIVA);
        vars.put("numeroReunion", acta.getNumeroReunion());
        vars.put("horaFin", acta.getHoraFin() != null ? acta.getHoraFin().toString() : null);
        vars.put("generadoEn", LocalDate.now().format(DATE_FMT));
        return vars;
    }
}
