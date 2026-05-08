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
            Pattern.compile("^#\\s+Reuni[oó]n\\s+(Socios|Directiva|socios|directiva)\\s+No\\.?\\s+((?:\\d{4}-)?\\d+)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_FECHA    =
            Pattern.compile("\\*\\*Fecha:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_HORA_INI =
            Pattern.compile("\\*\\*Hora inicio:\\*\\*\\s*(\\d{1,2}:\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_HORA_FIN =
            Pattern.compile("\\*\\*Hora fin:\\*\\*\\s*(\\d{1,2}:\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_TIPO     =
            Pattern.compile("\\*\\*Tipo de reuni[oó]n:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_PRESIDE  =
            Pattern.compile("\\*\\*Preside la Reuni[oó]n:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_SECR     =
            Pattern.compile("\\*\\*Secretaria:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_ASISTENTES =
            Pattern.compile("\\*\\*Asistentes:\\*\\*\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_ACUERDO  =
            Pattern.compile("\\*\\*Acuerdo:\\*\\*\\s*(.+)", Pattern.CASE_INSENSITIVE);

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
    // Entry point
    // =========================================================================

    public ActaImportPreviewResponse parsear(String contenido) {
        String[] lineas = contenido.split("\n");

        // Valores parseados
        TipoActa tipoActa           = null;
        Integer  numeroReunion      = null;
        LocalDate fecha             = null;
        LocalTime hora              = null;
        LocalTime horaFin           = null;
        String presideRaw           = null;
        String secretariaRaw        = null;
        List<String> asistentesRaw  = new ArrayList<>();

        // Secciones del desarrollo (formato clásico con ### subsecciones)
        boolean enDesarrollo            = false;
        int seccionActual               = 0; // 0=bloque principal, 1=actividades, 2=por realizar, 3=varios
        StringBuilder secActReal        = new StringBuilder(); // ### 1. Actividades realizadas
        StringBuilder secActPorReal     = new StringBuilder(); // ### 2. Actividades por realizar
        StringBuilder secVarios         = new StringBuilder(); // ### 3. Varios
        StringBuilder secDesarrolloPpal = new StringBuilder(); // bloque completo sin subsecciones (nuevo formato)
        List<String> acuerdosLista      = new ArrayList<>();

        // Sección "Orden del día" (nuevo formato directiva)
        boolean enOrdenDia              = false;
        StringBuilder secOrdenDia       = new StringBuilder();

        boolean asistentesEnSiguiente = false; // flag: la siguiente línea no vacía tiene los asistentes

        for (String linea : lineas) {
            String trim = linea.trim();

            // Asistentes en línea siguiente: se evalúa primero, antes de cualquier otro matcher,
            // para evitar que un `continue` previo deje el flag activo indefinidamente.
            if (asistentesEnSiguiente && !trim.isEmpty()) {
                asistentesRaw = parsearListaAsistentes(trim);
                asistentesEnSiguiente = false;
                continue;
            }

            // Título principal
            Matcher mTitulo = PAT_TITULO.matcher(trim);
            if (mTitulo.find()) {
                String tipo = mTitulo.group(1).toLowerCase();
                tipoActa = tipo.startsWith("d") ? TipoActa.DIRECTIVA : TipoActa.SOCIOS;
                String numeroRaw = mTitulo.group(2);
                if (numeroRaw.contains("-")) {
                    // Formato YYYY-NNNN (ej. 2026-0001) → extraer secuencia
                    numeroReunion = Integer.parseInt(numeroRaw.substring(numeroRaw.indexOf('-') + 1));
                } else {
                    numeroReunion = Integer.parseInt(numeroRaw);
                }
                continue;
            }

            // Detectar inicio de sección "Orden del día" (nuevo formato directiva)
            if (trim.matches("(?i)##\\s+Orden.*d[ií]a.*")) {
                enOrdenDia = true;
                enDesarrollo = false;
                continue;
            }

            // Detectar inicio de sección "Desarrollo"
            if (trim.matches("(?i)##\\s+Desarrollo.*")) {
                enDesarrollo = true;
                enOrdenDia = false;
                seccionActual = 0;
                continue;
            }

            // Detectar fin de sección "Orden del día" (otra H2 distinta)
            if (enOrdenDia && trim.matches("^##\\s+.*")) {
                enOrdenDia = false;
            }

            // Detectar fin del desarrollo (otra sección H2 distinta)
            if (enDesarrollo && trim.matches("^##\\s+(?!Desarrollo).*")) {
                enDesarrollo = false;
                seccionActual = 0;
            }

            // Acumular sección "Orden del día"
            if (enOrdenDia) {
                secOrdenDia.append(linea).append("\n");
                continue;
            }

            if (enDesarrollo) {
                // Detectar sub-secciones (formato clásico)
                if (PAT_SEC_ACTIVIDADES_REALIZADAS.matcher(trim).find()) {
                    seccionActual = 1; continue;
                }
                if (PAT_SEC_ACTIVIDADES_POR_REALIZAR.matcher(trim).find()) {
                    seccionActual = 2; continue;
                }
                if (PAT_SEC_VARIOS.matcher(trim).find()) {
                    seccionActual = 3; continue;
                }

                // Extraer acuerdos inline
                Matcher mAcuerdo = PAT_ACUERDO.matcher(trim);
                if (mAcuerdo.find()) {
                    acuerdosLista.add(mAcuerdo.group(1).trim());
                }

                // Acumular contenido en la sección correspondiente
                // caso 0: bloque principal sin subsecciones (nuevo formato directiva)
                switch (seccionActual) {
                    case 0 -> secDesarrolloPpal.append(linea).append("\n");
                    case 1 -> secActReal.append(linea).append("\n");
                    case 2 -> secActPorReal.append(linea).append("\n");
                    case 3 -> secVarios.append(linea).append("\n");
                }
                continue;
            }

            // Fecha
            Matcher mFecha = PAT_FECHA.matcher(trim);
            if (mFecha.find()) {
                fecha = parsearFechaEspanol(mFecha.group(1).trim());
                continue;
            }
            // Hora inicio
            Matcher mHoraIni = PAT_HORA_INI.matcher(trim);
            if (mHoraIni.find()) {
                hora = LocalTime.parse(mHoraIni.group(1), DateTimeFormatter.ofPattern("H:mm"));
                continue;
            }
            // Hora fin
            Matcher mHoraFin = PAT_HORA_FIN.matcher(trim);
            if (mHoraFin.find()) {
                horaFin = LocalTime.parse(mHoraFin.group(1), DateTimeFormatter.ofPattern("H:mm"));
                continue;
            }
            // Tipo reunión (puede redundar con el título; lo tomamos como override)
            Matcher mTipo = PAT_TIPO.matcher(trim);
            if (mTipo.find()) {
                String t = mTipo.group(1).trim().toLowerCase();
                tipoActa = t.startsWith("d") ? TipoActa.DIRECTIVA : TipoActa.SOCIOS;
                continue;
            }
            // Preside
            Matcher mPreside = PAT_PRESIDE.matcher(trim);
            if (mPreside.find()) {
                presideRaw = mPreside.group(1).trim();
                continue;
            }
            // Secretaria
            Matcher mSecr = PAT_SECR.matcher(trim);
            if (mSecr.find()) {
                secretariaRaw = mSecr.group(1).trim();
                continue;
            }
            // Asistentes: pueden estar en la misma línea o en la siguiente
            Matcher mAsistentes = PAT_ASISTENTES.matcher(trim);
            if (mAsistentes.find()) {
                String lista = mAsistentes.group(1).trim();
                if (!lista.isEmpty()) {
                    asistentesRaw = parsearListaAsistentes(lista);
                } else {
                    asistentesEnSiguiente = true;
                }
                continue;
            }
        }

        // Si tipoActa sigue null, default a SOCIOS
        if (tipoActa == null) tipoActa = TipoActa.SOCIOS;

        // Resolver nombres contra BD
        PersonaImportDto presidenteDto  = resolverPersona(presideRaw);
        PersonaImportDto secretariaDto  = resolverPersona(secretariaRaw);
        List<AsistenteImportDto> asistentesDtos = asistentesRaw.stream()
                .map(this::resolverAsistente)
                .toList();

        String acuerdosTexto = acuerdosLista.isEmpty() ? null
                : acuerdosLista.stream()
                        .map(a -> "- " + a)
                        .collect(Collectors.joining("\n"));

        // actividadesRealizadasDesc: subsección 1 (formato clásico) o bloque principal (nuevo formato)
        String actDescFinal = !secActReal.toString().strip().isEmpty()
                ? secActReal.toString().strip()
                : (secDesarrolloPpal.toString().strip().isEmpty() ? null : secDesarrolloPpal.toString().strip());

        // actividadesPorRealizar: subsección 2 (formato clásico) o "Orden del día" (nuevo formato)
        String actPorRealFinal = !secActPorReal.toString().strip().isEmpty()
                ? secActPorReal.toString().strip()
                : (secOrdenDia.toString().strip().isEmpty() ? null : secOrdenDia.toString().strip());

        boolean listaParaConfirmar = (presidenteDto == null || presidenteDto.resuelto())
                && (secretariaDto == null || secretariaDto.resuelto())
                && asistentesDtos.stream().allMatch(AsistenteImportDto::resuelto);

        return new ActaImportPreviewResponse(
                tipoActa,
                numeroReunion,
                fecha,
                hora,
                horaFin,
                null, // lugar: no está en el formato .md
                presidenteDto,
                secretariaDto,
                asistentesDtos,
                actDescFinal,
                actPorRealFinal,
                acuerdosTexto,
                secVarios.toString().strip().isEmpty() ? null : secVarios.toString().strip(),
                null,
                listaParaConfirmar
        );
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
        // Si no hay resultados con apellido, intentar solo por nombre
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
        // Ejemplo: "08 de abril de 2026"
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
