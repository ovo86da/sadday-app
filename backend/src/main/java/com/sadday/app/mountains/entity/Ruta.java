package com.sadday.app.mountains.entity;

import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rutas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ruta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", nullable = false, length = 20)
    private TipoActividad tipoActividad;

    /** Nullable: trekking y ciclismo pueden no estar ligados a una cima específica. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mountain_id")
    private Mountain mountain;

    /** Descripción libre del lugar cuando no hay montaña específica. */
    @Column(name = "lugar_referencia", length = 200)
    private String lugarReferencia;

    @Column(name = "sector_zona", length = 200)
    private String sectorZona;

    @Column(name = "longitud_km", precision = 6, scale = 2)
    private BigDecimal longitudKm;

    /** Desnivel acumulado de subida, en metros. Magnitud, siempre >= 0. */
    @Column(name = "desnivel_positivo_m")
    private Integer desnivelPositivoM;

    /**
     * Desnivel acumulado de bajada, en metros. Magnitud SIN signo: la travesia
     * Mojanda-San Pablo se guarda como 1248, no como -1248. El signo lo pone la
     * presentacion.
     *
     * {@code null} significa que no se conoce, no que la ruta no baje. Son
     * cosas distintas: sincholagua-camping baja 0 de verdad porque termina en
     * el campamento.
     */
    @Column(name = "desnivel_negativo_m")
    private Integer desnivelNegativoM;

    /** Para expediciones multi-día. */
    @Column(name = "duracion_dias")
    private Short duracionDias;

    /** Para salidas de un día (trekking, ciclismo). */
    @Column(name = "duracion_horas")
    private Short duracionHoras;

    @Column(name = "peligros_notas", columnDefinition = "TEXT")
    private String peligrosNotas;

    @Column(name = "requiere_permisos", nullable = false)
    private Boolean requierePermisos;

    @Column(name = "documentacion_url", columnDefinition = "TEXT")
    private String documentacionUrl;

    @Column(name = "track_url", columnDefinition = "TEXT")
    private String trackUrl;

    /** Nivel mínimo requerido para inscribirse en una salida de esta ruta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nivel_minimo_socio_id")
    private ClasificacionSocio nivelMinimoSocio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRuta estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisada_por_id")
    private Socio revisadaPor;

    @Column(name = "revisada_en")
    private LocalDateTime revisadaEn;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propuesta_por_id")
    private Socio propuestaPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Sub-entidades por tipo (cargadas bajo demanda) ──────────────────────
    @OneToOne(mappedBy = "ruta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private RutaAlpinismo alpinismo;

    @OneToOne(mappedBy = "ruta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private RutaEscalada escalada;

    @OneToOne(mappedBy = "ruta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private RutaTrekking trekking;

    @OneToOne(mappedBy = "ruta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private RutaCiclismo ciclismo;

    @OneToOne(mappedBy = "ruta", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private RutaIntegral integral;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (estado == null) estado = EstadoRuta.PENDIENTE;
        if (requierePermisos == null) requierePermisos = false;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
