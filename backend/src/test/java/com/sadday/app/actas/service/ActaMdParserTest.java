package com.sadday.app.actas.service;

import com.sadday.app.actas.dto.ActaImportPreviewResponse;
import com.sadday.app.actas.entity.TipoActa;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("ActaMdParser")
class ActaMdParserTest {

    @Mock
    private SocioRepository socioRepository;

    private ActaMdParser parser;

    // Socio de prueba para resolución de nombres
    private Socio socioJuan;
    private Socio socioAna;

    @BeforeEach
    void setUp() {
        parser = new ActaMdParser(socioRepository);

        socioJuan = new Socio();
        socioJuan.setId(UUID.randomUUID());
        socioJuan.setNombre("Juan");
        socioJuan.setApellido("Pérez");

        socioAna = new Socio();
        socioAna.setId(UUID.randomUUID());
        socioAna.setNombre("Ana");
        socioAna.setApellido("García");

        // Mockito retorna List.of() por defecto — sin stub adicional necesario
    }

    // =========================================================================
    // Formato clásico socios
    // =========================================================================

    private static final String ACTA_SOCIOS_COMPLETA = """
            # Reunión Socios No. 21
            ## Datos generales
            **Fecha:** 08 de abril de 2026
            **Hora inicio:** 18:00
            **Hora fin:** 19:30
            **Tipo de reunión:** Socios
            ## Identificación de autoridades y participantes
            **Preside la Reunión:** Juan Pérez
            **Secretaria:** Ana García
            **Asistentes:** Juan Pérez, Ana García, Carlos López
            ## Desarrollo de la reunión y compromisos
            ### 1. Actividades realizadas
            Se realizó la salida al Chimborazo.
            ### 2. Actividades por realizar
            Salida al Cotopaxi en mayo.
            ### 3. Varios
            **Acuerdo:** Comprar cuerdas nuevas.
            Ningún otro punto.
            """;

    @Nested
    @DisplayName("Cabecera")
    class Cabecera {

        @Test
        @DisplayName("parsea tipoActa SOCIOS y numeroReunion")
        void tipoYNumero() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_SOCIOS_COMPLETA);
            assertThat(r.tipoActa()).isEqualTo(TipoActa.SOCIOS);
            assertThat(r.numeroReunion()).isEqualTo(21);
        }

        @Test
        @DisplayName("parsea fecha en español")
        void fecha() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_SOCIOS_COMPLETA);
            assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, 4, 8));
        }

        @Test
        @DisplayName("parsea hora inicio y hora fin")
        void horas() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_SOCIOS_COMPLETA);
            assertThat(r.hora()).isEqualTo(LocalTime.of(18, 0));
            assertThat(r.horaFin()).isEqualTo(LocalTime.of(19, 30));
        }

        @Test
        @DisplayName("parsea asistentes en la misma línea")
        void asistentesMismaLinea() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_SOCIOS_COMPLETA);
            assertThat(r.asistentes()).hasSize(3);
        }

        @Test
        @DisplayName("asistentes en línea siguiente al campo")
        void asistentesSiguienteLinea() {
            String md = """
                    # Reunión Socios No. 5
                    **Asistentes:**
                    Juan Pérez, Ana García
                    """;
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.asistentes()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Tipo de acta")
    class TipoDeActa {

        @Test
        @DisplayName("detecta DIRECTIVA en el título")
        void directivaEnTitulo() {
            String md = "# Reunión Directiva No. 2026-0003\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.tipoActa()).isEqualTo(TipoActa.DIRECTIVA);
        }

        @Test
        @DisplayName("número en formato YYYY-NNNN extrae solo la secuencia")
        void numeroFormatoDirectiva() {
            String md = "# Reunión Directiva No. 2026-0007\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.numeroReunion()).isEqualTo(7);
        }

        @Test
        @DisplayName("título en minúsculas también reconoce el tipo")
        void tituloMinusculas() {
            String md = "# Reunión socios No. 3\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.tipoActa()).isEqualTo(TipoActa.SOCIOS);
        }

        @Test
        @DisplayName("campo Tipo de reunión sobreescribe el título")
        void tipoReunionSobreescribeTitulo() {
            String md = """
                    # Reunión Socios No. 1
                    **Tipo de reunión:** Directiva
                    """;
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.tipoActa()).isEqualTo(TipoActa.DIRECTIVA);
        }

        @Test
        @DisplayName("default a SOCIOS si no hay título")
        void defaultSocios() {
            ActaImportPreviewResponse r = parser.parsear("Sin título\n");
            assertThat(r.tipoActa()).isEqualTo(TipoActa.SOCIOS);
        }
    }

    @Nested
    @DisplayName("Secciones de desarrollo (formato clásico)")
    class SeccionesDesarrollo {

        @Test
        @DisplayName("extrae actividadesRealizadasDesc de subsección 1")
        void actividadesRealizadas() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_SOCIOS_COMPLETA);
            assertThat(r.actividadesRealizadasDesc()).contains("Chimborazo");
        }

        @Test
        @DisplayName("extrae actividadesPorRealizar de subsección 2")
        void actividadesPorRealizar() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_SOCIOS_COMPLETA);
            assertThat(r.actividadesPorRealizar()).contains("Cotopaxi");
        }

        @Test
        @DisplayName("extrae varios de subsección 3")
        void varios() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_SOCIOS_COMPLETA);
            assertThat(r.varios()).contains("Ningún otro punto");
        }

        @Test
        @DisplayName("extrae acuerdos inline de la sección varios")
        void acuerdosInline() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_SOCIOS_COMPLETA);
            assertThat(r.acuerdos()).contains("Comprar cuerdas nuevas");
        }

        @Test
        @DisplayName("múltiples acuerdos se concatenan con guión")
        void multiplesAcuerdos() {
            String md = """
                    # Reunión Socios No. 1
                    ## Desarrollo de la reunión y compromisos
                    ### 3. Varios
                    **Acuerdo:** Primer acuerdo.
                    **Acuerdo:** Segundo acuerdo.
                    """;
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.acuerdos())
                    .contains("Primer acuerdo")
                    .contains("Segundo acuerdo");
        }
    }

    @Nested
    @DisplayName("Formato nuevo directiva (Orden del día)")
    class FormatoNuevoDirectiva {

        private static final String ACTA_DIRECTIVA = """
                # Reunión Directiva No. 2026-0001
                ## Datos generales
                **Fecha:** 15 de enero de 2026
                **Hora inicio:** 10:00
                ## Orden del día
                Punto 1: Presupuesto anual.
                Punto 2: Nuevos proyectos.
                ## Desarrollo de la reunión y compromisos
                Informe del tesorero presentado.
                """;

        @Test
        @DisplayName("acumula bloque Orden del día en actividadesPorRealizar")
        void ordenDiaEnActividadesPorRealizar() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_DIRECTIVA);
            assertThat(r.actividadesPorRealizar()).contains("Presupuesto anual");
        }

        @Test
        @DisplayName("bloque principal Desarrollo va en actividadesRealizadasDesc")
        void desarrolloPrincipalEnActividadesRealizadas() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_DIRECTIVA);
            assertThat(r.actividadesRealizadasDesc()).contains("tesorero");
        }

        @Test
        @DisplayName("otra sección H2 cierra Orden del día")
        void otraSeccionCierraOrdenDia() {
            ActaImportPreviewResponse r = parser.parsear(ACTA_DIRECTIVA);
            // El texto de Desarrollo no debe aparecer en actividadesPorRealizar
            assertThat(r.actividadesPorRealizar()).doesNotContain("tesorero");
        }
    }

    @Nested
    @DisplayName("Resolución de nombres")
    class ResolucionNombres {

        @Test
        @DisplayName("nombre que resuelve a un único socio queda resuelto=true")
        void nombreResueltoPorBD() {
            when(socioRepository.buscarPorNombreYApellido("Juan", "Pérez"))
                    .thenReturn(List.of(socioJuan));

            String md = """
                    # Reunión Socios No. 1
                    **Preside la Reunión:** Juan Pérez
                    """;
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.presidenteReunion().resuelto()).isTrue();
            assertThat(r.presidenteReunion().socioId()).isEqualTo(socioJuan.getId());
        }

        @Test
        @DisplayName("nombre ambiguo (múltiples candidatos) queda resuelto=false")
        void nombreAmbiguo() {
            Socio otro = new Socio();
            otro.setId(UUID.randomUUID());
            otro.setNombre("Juan");
            otro.setApellido("Pérez");
            when(socioRepository.buscarPorNombreYApellido("Juan", "Pérez"))
                    .thenReturn(List.of(socioJuan, otro));

            String md = "# Reunión Socios No. 1\n**Preside la Reunión:** Juan Pérez\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.presidenteReunion().resuelto()).isFalse();
            assertThat(r.presidenteReunion().candidatos()).hasSize(2);
        }

        @Test
        @DisplayName("nombre no encontrado queda resuelto=false sin candidatos")
        void nombreNoEncontrado() {
            String md = "# Reunión Socios No. 1\n**Secretaria:** Desconocida Persona\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.secretariaReunion().resuelto()).isFalse();
            assertThat(r.secretariaReunion().candidatos()).isEmpty();
        }

        @Test
        @DisplayName("listaParaConfirmar=true cuando todos los nombres se resuelven")
        void listaParaConfirmar() {
            when(socioRepository.buscarPorNombreYApellido("Juan", "Pérez"))
                    .thenReturn(List.of(socioJuan));
            when(socioRepository.buscarPorNombreYApellido("Ana", "García"))
                    .thenReturn(List.of(socioAna));

            String md = """
                    # Reunión Socios No. 1
                    **Preside la Reunión:** Juan Pérez
                    **Secretaria:** Ana García
                    """;
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.listaParaConfirmar()).isTrue();
        }

        @Test
        @DisplayName("listaParaConfirmar=false si algún nombre no se resuelve")
        void listaNoConfirmada() {
            String md = """
                    # Reunión Socios No. 1
                    **Preside la Reunión:** Juan Pérez
                    **Secretaria:** Desconocida Persona
                    """;
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.listaParaConfirmar()).isFalse();
        }
    }

    @Nested
    @DisplayName("Fecha en español")
    class FechaEspanol {

        @Test
        @DisplayName("parsea todos los meses correctamente")
        void todosMeses() {
            String[] meses = {"enero","febrero","marzo","abril","mayo","junio",
                              "julio","agosto","septiembre","octubre","noviembre","diciembre"};
            for (int i = 0; i < meses.length; i++) {
                String md = "# Reunión Socios No. 1\n**Fecha:** 01 de " + meses[i] + " de 2026\n";
                ActaImportPreviewResponse r = parser.parsear(md);
                assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, i + 1, 1));
            }
        }

        @Test
        @DisplayName("fecha con mes desconocido retorna null")
        void mesDesconocido() {
            String md = "# Reunión Socios No. 1\n**Fecha:** 01 de enerox de 2026\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.fecha()).isNull();
        }
    }

    @Nested
    @DisplayName("Casos edge")
    class CasosEdge {

        @Test
        @DisplayName("contenido vacío no lanza excepción")
        void contenidoVacio() {
            ActaImportPreviewResponse r = parser.parsear("");
            assertThat(r.tipoActa()).isEqualTo(TipoActa.SOCIOS);
            assertThat(r.numeroReunion()).isNull();
        }

        @Test
        @DisplayName("secciones vacías retornan null en los campos correspondientes")
        void seccionesVacias() {
            String md = "# Reunión Socios No. 1\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.actividadesRealizadasDesc()).isNull();
            assertThat(r.actividadesPorRealizar()).isNull();
            assertThat(r.varios()).isNull();
            assertThat(r.acuerdos()).isNull();
        }

        @Test
        @DisplayName("título con acento en Reunión es reconocido")
        void tituloConAcento() {
            String md = "# Reunión Socios No. 10\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.numeroReunion()).isEqualTo(10);
        }

        @Test
        @DisplayName("título con Reunion sin acento también es reconocido")
        void tituloSinAcento() {
            String md = "# Reunion Socios No. 10\n";
            ActaImportPreviewResponse r = parser.parsear(md);
            assertThat(r.numeroReunion()).isEqualTo(10);
        }
    }
}
