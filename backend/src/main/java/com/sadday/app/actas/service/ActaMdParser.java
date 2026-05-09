package com.sadday.app.actas.service;

import com.sadday.app.actas.dto.*;
import com.sadday.app.actas.entity.TipoActa;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parser del formato fijo de actas de reunión del Club de Montaña Sadday.
 *
 * <p>El formato esperado es:
 * <pre>
 * # Reunión (Socios|Directiva) No. N
 * ## Datos generales
 *   **Fecha:** DD de mes de YYYY
 *   **Hora inicio:** HH:MM
 *   **Hora fin:** HH:MM
 *   **Tipo de reunión:** Socios|Directiva
 * ## Identificación de autoridades y participantes
 *   **Preside la Reunión:** Nombre Apellido
 *   **Secretaria:** Nombre Apellido
 *   **Asistentes:** Nombre1, Nombre2, ...
 * ## Desarrollo de la reunión y compromisos
 *   ### 1. Actividades realizadas   ← texto completo
 *   ### 2. Actividades por realizar ← texto completo
 *   ### 3. Varios                   ← texto completo (con **Acuerdo:** embebidos)
 * </pre>
 *
 * <p>Los acuerdos se extraen de todas las líneas que contengan {@code **Acuerdo:**}
 * en la sección de desarrollo y se concatenan en el campo {@code acuerdos}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActaMdParser {

    private final SocioRepository socioRepository;

    // --- Patrones de cabecera ---
    // Soporta "No. 21" (formato socios) y "No. 2026-0001" (formato directiva)
    private static final Pattern PAT_TITULO   =
            Pattern.compile("^#\\s+Reuni[oó]n\\s+(Socios|Directiva)\\s+No\\.?\\s+((?:\\d{4}-)?\\d+)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PAT_FECHA    =
            Pattern.compile("\\*\\*Fecha:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_HORA_INI =
            Pattern.compile("\\*\\*Hora inicio:\\*\\*\\s*(\\d{1,2}:\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_HORA_FIN =
            Pattern.compile("\\*\\*Hora fin:\\*\\*\\s*(\\d{1,2}:\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_TIPO     =
            Pattern.compile("\\*\\*Tipo de reuni[oó]n:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PAT_PRESIDE  =
            Pattern.compile("\\*\\*Preside la Reuni[oó]n:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PAT_SECR     =
            Pattern.compile("\\*\\*Secretaria:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_ASISTENTES =
            Pattern.compile("\\*\\*Asistentes:\\*\\*\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_ACUERDO  =
            Pattern.compile("\\*\\*Acuerdo:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE);

    // Detección de secciones H2 — sin .* al final para evitar backtracking (S2631)
    private static final Pattern PAT_H2_ORDEN_DIA     = Pattern.compile("(?i)##\\s+Orden.*d[ií]a");
    private static final Pattern PAT_H2_DESARROLLO    = Pattern.compile("(?i)##\\s+Desarrollo");
    private static final Pattern PAT_H2_ANY           = Pattern.compile("^##\\s+");
    private static final Pattern PAT_H2_NO_DESARROLLO = Pattern.compile("^##\\s+(?!Desarrollo)");

    // Secciones de desarrollo
    private static final Pattern PAT_SEC_ACTIVIDADES_REALIZADAS =
            Pattern.compile("^###\\s*1\\.\\s*Actividades realizadas", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_SEC_ACTIVIDADES_POR_REALIZAR =
            Pattern.compile("^###\\s*2\\.\\s*Actividades por realizar", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_SEC_VARIOS =
            Pattern.compile("^###\\s*3\\.\\s*Varios", Pattern.CASE_INSENSITIVE);

    // Meses en español
    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3),
            Map.entry("abril", 4), Map.entry("mayo", 5), Map.entry("junio", 6),
            Map.entry("julio", 7), Map.entry("agosto", 8), Map.entry("septiembre", 9),
            Map.entry("octubre", 10), Map.entry("noviembre", 11), Map.entry("diciembre", 12)
    );

    // =========================================================================
    // Estado interno del parser
    // =========================================================================

    private static final class ParseState {
        TipoActa  tipoActa;
        Integer   numeroReunion;
        LocalDate fecha;
        LocalTime hora;
        LocalTime horaFin;
        String    presideRaw;
        String    secretariaRaw;
        List<String> asistentesRaw    = new ArrayList<>();
        boolean      asistentesEnSiguiente = false;

        boolean enDesarrollo  = false;
        boolean enOrdenDia    = false;
        int     seccionActual = 0; // 0=principal, 1=actividades, 2=por realizar, 3=varios

        StringBuilder secActReal        = new StringBuilder();
        StringBuilder secActPorReal     = new StringBuilder();
        StringBuilder secVarios         = new StringBuilder();
        StringBuilder secDesarrolloPpal = new StringBuilder();
        StringBuilder secOrdenDia       = new StringBuilder();
        List<String>  acuerdosLista     = new ArrayList<>();
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public ActaImportPreviewResponse parsear(String contenido) {
        ParseState s = new ParseState();
        for (String linea : contenido.split("\n")) {
            procesarLinea(s, linea);
        }
        if (s.tipoActa == null) s.tipoActa = TipoActa.SOCIOS;
        return construirRespuesta(s);
    }

    // =========================================================================
    // Procesamiento línea a línea
    // =========================================================================

    private void procesarLinea(ParseState s, String linea) {
        String trim = linea.trim();

        if (s.asistentesEnSiguiente && !trim.isEmpty()) {
            s.asistentesRaw = parsearListaAsistentes(trim);
            s.asistentesEnSiguiente = false;
            return;
        }

        if (procesarTitulo(s, trim))    return;
        if (esInicioSeccion(s, trim))   return;

        detectarFinSeccion(s, trim);

        if (s.enOrdenDia)   { s.secOrdenDia.append(linea).append("\n"); return; }
        if (s.enDesarrollo) { procesarLineaDesarrollo(s, linea, trim);  return; }
        procesarLineaCabecera(s, trim);
    }

    private boolean procesarTitulo(ParseState s, String trim) {
        Matcher m = PAT_TITULO.matcher(trim);
        if (!m.find()) return false;
        s.tipoActa = m.group(1).toLowerCase().startsWith("d") ? TipoActa.DIRECTIVA : TipoActa.SOCIOS;
        String raw = m.group(2);
        s.numeroReunion = raw.contains("-")
                ? Integer.parseInt(raw.substring(raw.indexOf('-') + 1))
                : Integer.parseInt(raw);
        return true;
    }

    private boolean esInicioSeccion(ParseState s, String trim) {
        if (PAT_H2_ORDEN_DIA.matcher(trim).find()) {
            s.enOrdenDia = true; s.enDesarrollo = false;
            return true;
        }
        if (PAT_H2_DESARROLLO.matcher(trim).find()) {
            s.enDesarrollo = true; s.enOrdenDia = false; s.seccionActual = 0;
            return true;
        }
        return false;
    }

    private void detectarFinSeccion(ParseState s, String trim) {
        if (s.enOrdenDia   && PAT_H2_ANY.matcher(trim).find())           s.enOrdenDia   = false;
        if (s.enDesarrollo && PAT_H2_NO_DESARROLLO.matcher(trim).find()) { s.enDesarrollo = false; s.seccionActual = 0; }
    }

    private void procesarLineaDesarrollo(ParseState s, String linea, String trim) {
        if (PAT_SEC_ACTIVIDADES_REALIZADAS.matcher(trim).find())   { s.seccionActual = 1; return; }
        if (PAT_SEC_ACTIVIDADES_POR_REALIZAR.matcher(trim).find()) { s.seccionActual = 2; return; }
        if (PAT_SEC_VARIOS.matcher(trim).find())                   { s.seccionActual = 3; return; }

        Matcher mAcuerdo = PAT_ACUERDO.matcher(trim);
        if (mAcuerdo.find()) s.acuerdosLista.add(mAcuerdo.group(1).trim());

        switch (s.seccionActual) {
            case 1 -> s.secActReal.append(linea).append("\n");
            case 2 -> s.secActPorReal.append(linea).append("\n");
            case 3 -> s.secVarios.append(linea).append("\n");
            default -> s.secDesarrolloPpal.append(linea).append("\n");
        }
    }

    private void procesarLineaCabecera(ParseState s, String trim) {
        Matcher mFecha = PAT_FECHA.matcher(trim);
        if (mFecha.find()) { s.fecha = parsearFechaEspanol(mFecha.group(1).trim()); return; }

        Matcher mHoraIni = PAT_HORA_INI.matcher(trim);
        if (mHoraIni.find()) { s.hora = LocalTime.parse(mHoraIni.group(1), DateTimeFormatter.ofPattern("H:mm")); return; }

        Matcher mHoraFin = PAT_HORA_FIN.matcher(trim);
        if (mHoraFin.find()) { s.horaFin = LocalTime.parse(mHoraFin.group(1), DateTimeFormatter.ofPattern("H:mm")); return; }

        Matcher mTipo = PAT_TIPO.matcher(trim);
        if (mTipo.find()) { s.tipoActa = mTipo.group(1).trim().toLowerCase().startsWith("d") ? TipoActa.DIRECTIVA : TipoActa.SOCIOS; return; }

        Matcher mPreside = PAT_PRESIDE.matcher(trim);
        if (mPreside.find()) { s.presideRaw = mPreside.group(1).trim(); return; }

        Matcher mSecr = PAT_SECR.matcher(trim);
        if (mSecr.find()) { s.secretariaRaw = mSecr.group(1).trim(); return; }

        Matcher mAsistentes = PAT_ASISTENTES.matcher(trim);
        if (mAsistentes.find()) {
            String lista = mAsistentes.group(1).trim();
            if (!lista.isEmpty()) s.asistentesRaw = parsearListaAsistentes(lista);
            else s.asistentesEnSiguiente = true;
        }
    }

    // =========================================================================
    // Construcción de la respuesta
    // =========================================================================

    private ActaImportPreviewResponse construirRespuesta(ParseState s) {
        PersonaImportDto presidenteDto = resolverPersona(s.presideRaw);
        PersonaImportDto secretariaDto = resolverPersona(s.secretariaRaw);
        List<AsistenteImportDto> asistentesDtos = s.asistentesRaw.stream()
                .map(this::resolverAsistente)
                .toList();

        String acuerdosTexto = s.acuerdosLista.isEmpty() ? null
                : s.acuerdosLista.stream().map(a -> "- " + a).collect(Collectors.joining("\n"));

        // actDescFinal: subsección 1 (formato clásico) o bloque principal (nuevo formato)
        String actDescFinal    = resolverConFallback(s.secActReal,    s.secDesarrolloPpal);
        // actPorRealFinal: subsección 2 (formato clásico) o "Orden del día" (nuevo formato)
        String actPorRealFinal = resolverConFallback(s.secActPorReal, s.secOrdenDia);

        boolean listaParaConfirmar = (presidenteDto == null || presidenteDto.resuelto())
                && (secretariaDto == null || secretariaDto.resuelto())
                && asistentesDtos.stream().allMatch(AsistenteImportDto::resuelto);

        return new ActaImportPreviewResponse(
                s.tipoActa, s.numeroReunion, s.fecha, s.hora, s.horaFin,
                null, presidenteDto, secretariaDto, asistentesDtos,
                actDescFinal, actPorRealFinal, acuerdosTexto,
                resolverConFallback(s.secVarios, null),
                null, listaParaConfirmar
        );
    }

    private String resolverConFallback(StringBuilder primario, StringBuilder fallback) {
        String p = primario != null ? primario.toString().strip() : "";
        if (!p.isEmpty()) return p;
        if (fallback == null) return null;
        String f = fallback.toString().strip();
        return f.isEmpty() ? null : f;
    }

    // =========================================================================
    // Resolución de nombres
    // =========================================================================

    private PersonaImportDto resolverPersona(String nombreRaw) {
        if (nombreRaw == null || nombreRaw.isBlank()) return null;
        List<Socio> candidatos = buscarCandidatos(nombreRaw);
        if (candidatos.size() == 1) {
            Socio s = candidatos.get(0);
            return new PersonaImportDto(nombreRaw, true,
                    s.getId(), s.getNombre(), s.getApellido(), List.of());
        }
        return new PersonaImportDto(nombreRaw, false, null, null, null,
                candidatos.stream()
                        .map(s -> new CandidatoSocioDto(s.getId(), s.getNombre(), s.getApellido()))
                        .toList());
    }

    private AsistenteImportDto resolverAsistente(String nombreRaw) {
        List<Socio> candidatos = buscarCandidatos(nombreRaw);
        if (candidatos.size() == 1) {
            Socio s = candidatos.get(0);
            return new AsistenteImportDto(nombreRaw, true,
                    s.getId(), s.getNombre(), s.getApellido(), List.of());
        }
        return new AsistenteImportDto(nombreRaw, false, null, null, null,
                candidatos.stream()
                        .map(s -> new CandidatoSocioDto(s.getId(), s.getNombre(), s.getApellido()))
                        .toList());
    }

    /**
     * Divide el nombre en tokens y busca usando el primero como nombre y el último como apellido.
     * Ejemplo: "Juan Carlos Jara" → nombre="Juan", apellido="Jara"
     * "Doris I. Sosa Bolaños" → nombre="Doris", apellido="Bolaños"
     */
    private List<Socio> buscarCandidatos(String nombreCompleto) {
        String[] tokens = nombreCompleto.trim().split("\\s+");
        String nombre   = tokens[0];
        String apellido = tokens.length > 1 ? tokens[tokens.length - 1] : null;
        List<Socio> resultado = socioRepository.buscarPorNombreYApellido(nombre, apellido);
        if (resultado.isEmpty() && apellido != null) {
            resultado = socioRepository.buscarPorNombreYApellido(nombre, null);
        }
        return resultado;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<String> parsearListaAsistentes(String linea) {
        return Arrays.stream(linea.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Parsea fechas en formato "08 de abril de 2026".
     */
    private LocalDate parsearFechaEspanol(String texto) {
        String[] partes = texto.toLowerCase().replaceAll("\\bde\\b", "").trim().split("\\s+");
        try {
            int dia  = Integer.parseInt(partes[0]);
            int mes  = MESES.getOrDefault(partes[1], -1);
            int anio = Integer.parseInt(partes[partes.length - 1]);
            if (mes == -1) {
                log.warn("Mes no reconocido en fecha: '{}'", texto);
                return null;
            }
            return LocalDate.of(anio, mes, dia);
        } catch (Exception e) {
            log.warn("No se pudo parsear la fecha: '{}'", texto);
            return null;
        }
    }
}
