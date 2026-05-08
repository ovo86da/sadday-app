package com.sadday.app.informes.service;

import com.sadday.app.informes.entity.InformeSalida;
import com.sadday.app.informes.entity.InformeSalidaReconocimiento;
import com.sadday.app.informes.repository.InformeSalidaReconocimientoRepository;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.salidas.entity.SalidaParticipante;
import com.sadday.app.salidas.entity.SalidaParticipanteDignidad;
import com.sadday.app.salidas.repository.SalidaParticipanteDignidadRepository;
import com.sadday.app.salidas.repository.SalidaParticipanteRepository;
import com.sadday.app.shared.entity.Documento;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.pdf.DocumentoService;
import com.sadday.app.shared.pdf.PdfRenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Genera y sirve el PDF de un Informe de Salida.
 *
 * <p>Flujo de generación:
 * <ol>
 *   <li>Carga el informe y datos relacionados (participantes, reconocimientos)</li>
 *   <li>Construye el mapa de variables para el template Thymeleaf</li>
 *   <li>Llama a {@link PdfRenderService} para obtener los bytes PDF</li>
 *   <li>Llama a {@link DocumentoService} para subir a S3 y persistir metadatos</li>
 *   <li>Actualiza el campo {@code documento} del informe</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfInformeService {

    private final InformeSalidaRepository               informeRepository;
    private final InformeSalidaReconocimientoRepository reconocimientoRepository;
    private final SalidaParticipanteRepository          participanteRepository;
    private final SalidaParticipanteDignidadRepository  dignidadRepository;
    private final PdfRenderService                      pdfRenderService;
    private final DocumentoService                      documentoService;

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Genera (o regenera) el PDF y lo retorna para descarga inmediata.
     * Intenta subir a S3 para persistencia, pero si S3 no está disponible
     * (dev sin MinIO) devuelve los bytes igualmente desde memoria.
     */
    @Transactional
    public byte[] generarYDescargarPdf(UUID salidaId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));
        if (informe.getValidadoPor() == null) {
            throw new BusinessException(ErrorCode.INFORME_NO_VALIDADO);
        }
        byte[] pdf = renderBytes(salidaId);
        subirAStorage(salidaId, pdf);
        return pdf;
    }

    /** Renderiza el template y devuelve los bytes del PDF sin tocar el storage. */
    private byte[] renderBytes(UUID salidaId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));

        List<SalidaParticipante> participantes = participanteRepository.findBySalidaId(salidaId);
        List<InformeSalidaReconocimiento> reconocimientos =
                reconocimientoRepository.findByInformeId(informe.getId());

        List<Long> participanteIds = participantes.stream().map(SalidaParticipante::getId).toList();
        List<SalidaParticipanteDignidad> dignidades = participanteIds.isEmpty()
                ? List.of()
                : dignidadRepository.findByParticipanteIdIn(participanteIds);

        Map<String, Object> vars = buildVars(informe, participantes, dignidades, reconocimientos);
        return pdfRenderService.render("informe-salida", vars);
    }

    /** Sube el PDF a S3 y actualiza la FK en BD. Si S3 falla, registra un warning y continúa. */
    private Documento subirAStorage(UUID salidaId, byte[] pdf) {
        String fecha     = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String filename  = "informe-salida-" + salidaId + "-" + fecha + ".pdf";
        String objectKey = "informes/" + salidaId + "/" + filename;
        try {
            InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));
            Documento doc = documentoService.guardar(pdf, objectKey, filename);
            informe.setDocumento(doc);
            informeRepository.save(informe);
            return doc;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo subir el PDF de informe a S3 (continúa sin persistir): {}", e.getMessage(), e);
            return null;
        }
    }

    /** Descarga los bytes del PDF ya generado. Solo disponible si el informe está validado. */
    @Transactional(readOnly = true)
    public byte[] descargarPdf(UUID salidaId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));
        if (informe.getValidadoPor() == null) {
            throw new BusinessException(ErrorCode.INFORME_NO_VALIDADO);
        }
        if (informe.getDocumento() == null) {
            throw new BusinessException(ErrorCode.DOCUMENTO_NO_GENERADO);
        }
        return documentoService.descargar(informe.getDocumento());
    }

    /** Devuelve el filename del documento para el header Content-Disposition. */
    @Transactional(readOnly = true)
    public String getFilename(UUID salidaId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));
        if (informe.getDocumento() != null) {
            return informe.getDocumento().getFilename();
        }
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return "informe-salida-" + salidaId + "-" + fecha + ".pdf";
    }

    /** Devuelve el documento del informe (metadatos completos, incluido checksum). Null si aún no se generó. */
    @Transactional(readOnly = true)
    public Documento getDocumento(UUID salidaId) {
        InformeSalida informe = informeRepository.findBySalidaId(salidaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INFORME_NOT_FOUND));
        return informe.getDocumento();
    }

    private Map<String, Object> buildVars(InformeSalida informe,
                                          List<SalidaParticipante> participantes,
                                          List<SalidaParticipanteDignidad> dignidades,
                                          List<InformeSalidaReconocimiento> reconocimientos) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("salidaNombre", informe.getSalida().getNombre());
        vars.put("salidaFecha", informe.getSalida().getFechaInicio() != null
                ? informe.getSalida().getFechaInicio().format(DATE_FMT) : "—");
        vars.put("salidaRuta", informe.getSalida().getRuta() != null
                ? informe.getSalida().getRuta().getNombre() : null);
        vars.put("seRealizo", informe.getSeRealizo());
        vars.put("lograronCumbre", informe.getLograronCumbre());
        vars.put("condicionesMeterologicas", informe.getCondicionesMeterologicas());
        vars.put("horaSalidaClub",      informe.getHoraSalidaClub());
        vars.put("horaLlegadaMontana",  informe.getHoraLlegadaMontana());
        vars.put("horaCumbre",          informe.getHoraCumbre());
        vars.put("horaInicioDescenso",  informe.getHoraInicioDescenso());
        vars.put("horaLlegadaAutos",    informe.getHoraLlegadaAutos());
        vars.put("horaRegresoClub",     informe.getHoraRegresoClub());
        vars.put("cronica",             informe.getCronica());
        vars.put("observaciones",       informe.getObservaciones());
        vars.put("comentariosVarios",   informe.getComentariosVarios());
        vars.put("segmentos",           informe.getSegmentos());
        vars.put("alquiloAlgunTransporte", informe.alquiloAlgunTransporte());
        vars.put("alquiloGuia",         informe.getAlquiloGuia());
        vars.put("costoGuia",           informe.getCostoGuia());
        vars.put("contactoGuiaNombre",   informe.getContactoGuia() != null ? informe.getContactoGuia().getNombre() : null);
        vars.put("contactoGuiaTelefono", informe.getContactoGuia() != null ? informe.getContactoGuia().getTelefono() : null);
        vars.put("alquiloRefugio",      informe.getAlquiloRefugio());
        vars.put("nombreRefugio",       informe.getNombreRefugio());
        vars.put("costoRefugio",        informe.getCostoRefugio());
        vars.put("acampo",              informe.getAcampo());
        vars.put("nombreCamping",       informe.getNombreCamping());
        vars.put("costoCamping",        informe.getCostoCamping());
        vars.put("costoTotal",          informe.getCostoTotal());
        // Determinar el jefe de salida desde la tabla de dignidades
        java.util.Set<Long> jefeParticipanteIds = dignidades.stream()
                .filter(d -> "Jefe de Salida".equals(d.getDignidad().getNombre()))
                .map(d -> d.getParticipante().getId())
                .collect(java.util.stream.Collectors.toSet());
        vars.put("jefeNombre", participantes.stream()
                .filter(p -> jefeParticipanteIds.contains(p.getId()))
                .findFirst()
                .map(p -> p.getSocio().getNombre() + " " + p.getSocio().getApellido())
                .orElse(null));
        vars.put("validadoPorNombre", informe.getValidadoPor() != null
                ? informe.getValidadoPor().getNombre() + " " + informe.getValidadoPor().getApellido()
                : null);
        vars.put("validadoEn", informe.getValidadoEn() != null
                ? informe.getValidadoEn().format(DATETIME_FMT) : null);
        vars.put("participantes", participantes.stream()
                .map(p -> p.getSocio().getNombre() + " " + p.getSocio().getApellido())
                .toList());
        vars.put("reconocimientos", reconocimientos.stream()
                .map(r -> Map.of(
                        "nombre", r.getSocio().getNombre() + " " + r.getSocio().getApellido(),
                        "tipo",   r.getTipo().name(),
                        "motivo", r.getMotivo()))
                .toList());
        vars.put("generadoEn", LocalDate.now().format(DATE_FMT));
        return vars;
    }
}
