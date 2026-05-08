package com.sadday.app.informes.entity;

import com.sadday.app.mountains.entity.Contacto;
import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.shared.entity.Documento;
import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Informe post-salida generado por el Jefe de Salida.
 *
 * <p>Existe a lo sumo uno por {@link Salida} (UNIQUE en BD).
 * El campo {@code validadoPor} registra la firma del Directivo ("Jefe de Montaña").
 * Una vez validado, el informe no puede modificarse.
 *
 * <p>El PDF exportado se almacena en S3. Los metadatos se guardan en {@link Documento}.
 */
@Entity
@Table(name = "informe_salida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InformeSalida {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salida_id", nullable = false, unique = true)
    private Salida salida;

    @Column(name = "condiciones_meteorologicas")
    private String condicionesMeterologicas;

    @Column(name = "se_realizo", nullable = false)
    private Boolean seRealizo;

    /** ¿El grupo logró llegar a la cumbre? Puede ser false aunque seRealizo=true (p.ej. por clima). */
    @Column(name = "logro_cumbre", nullable = false)
    private Boolean lograronCumbre;

    @Column(name = "hora_salida_club")
    private LocalTime horaSalidaClub;

    @Column(name = "hora_llegada_montana")
    private LocalTime horaLlegadaMontana;

    @Column(name = "hora_cumbre")
    private LocalTime horaCumbre;

    @Column(name = "hora_inicio_descenso")
    private LocalTime horaInicioDescenso;

    @Column(name = "hora_llegada_autos")
    private LocalTime horaLlegadaAutos;

    @Column(name = "hora_regreso_club")
    private LocalTime horaRegresoClub;

    @Column(columnDefinition = "TEXT")
    private String cronica;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "comentarios_varios", columnDefinition = "TEXT")
    private String comentariosVarios;

    // ── Segmentos de viaje ──────────────────────────────────────────────────
    @OneToMany(mappedBy = "informe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<SegmentoViaje> segmentos = new ArrayList<>();

    // ── Guía externo ────────────────────────────────────────────────────────
    @Column(name = "alquilo_guia", nullable = false)
    private Boolean alquiloGuia;

    @Column(name = "costo_guia", precision = 10, scale = 2)
    private BigDecimal costoGuia;

    /** Contacto externo (empresa/persona) cuando se contrató guía. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacto_guia_id")
    private Contacto contactoGuia;

    // ── Alojamiento: Refugio ────────────────────────────────────────────────
    @Column(name = "alquilo_refugio", nullable = false)
    private Boolean alquiloRefugio;

    @Column(name = "nombre_refugio", length = 200)
    private String nombreRefugio;

    @Column(name = "costo_refugio", precision = 8, scale = 2)
    private BigDecimal costoRefugio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacto_refugio_id")
    private Contacto contactoRefugio;

    // ── Alojamiento: Camping ────────────────────────────────────────────────
    @Column(name = "acampo", nullable = false)
    private Boolean acampo;

    @Column(name = "nombre_camping", length = 200)
    private String nombreCamping;

    @Column(name = "costo_camping", precision = 8, scale = 2)
    private BigDecimal costoCamping;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacto_camping_id")
    private Contacto contactoCamping;

    // ── Autos ────────────────────────────────────────────────────────────────
    /** Dónde se dejaron los autos. Null si no aplica o no se informó. */
    @Enumerated(EnumType.STRING)
    @Column(name = "donde_autos", length = 30)
    private DondeAutos dondeAutos;

    /** Dirección o descripción del lugar. Requerido cuando dondeAutos != NO_AUTOS. */
    @Column(name = "autos_descripcion", length = 300)
    private String autosDescripcion;

    /** Link de ubicación opcional (Google Maps, etc.). */
    @Column(name = "autos_link_ubicacion", length = 500)
    private String autosLinkUbicacion;

    /** Costo del parqueadero por persona. Solo aplica cuando dondeAutos es PARQUEADERO_*. */
    @Column(name = "costo_parqueadero", precision = 8, scale = 2)
    private BigDecimal costoParqueadero;

    // ── Costos ──────────────────────────────────────────────────────────────
    /** Costo total del viaje (suma de todos los gastos). */
    @Column(name = "costo_total", precision = 10, scale = 2)
    private BigDecimal costoTotal;

    /** Costo por persona, ingresado manualmente por el jefe de salida. */
    @Column(name = "costo_por_persona", precision = 10, scale = 2)
    private BigDecimal costoPorPersona;

    // ── Validación ──────────────────────────────────────────────────────────
    /** Directivo que firma/valida el informe. Null mientras no esté validado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validado_por_id")
    private Socio validadoPor;

    @Column(name = "validado_en")
    private LocalDateTime validadoEn;

    /** PDF generado, almacenado en S3. Null hasta que se genere el primer PDF. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isValidado() {
        return validadoEn != null;
    }

    /** ¿Al menos un segmento de viaje alquiló transporte externo? */
    public boolean alquiloAlgunTransporte() {
        return segmentos.stream().anyMatch(s -> Boolean.TRUE.equals(s.getAlquiloTransporte()));
    }
}
