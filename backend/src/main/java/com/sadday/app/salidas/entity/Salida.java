package com.sadday.app.salidas.entity;

import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.entity.TipoActividad;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Salida del club de montaña.
 *
 * <p>Una salida parte de una {@link Ruta} aprobada y tiene un {@code estado} que avanza de
 * PLANIFICADA → EN_CURSO → REALIZADA (o CANCELADA en cualquier punto previo a REALIZADA).
 *
 * <p>El {@code nivelMinimoRequerido} puede ser calculado de la ruta (al cruzar sus dificultades
 * con {@code acceso_ruta_por_nivel}) o establecido manualmente por un Directivo (solo al alza).
 */
@Entity
@Table(name = "salida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Salida {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "hora_encuentro_club", nullable = false)
    private LocalTime horaEncuentroClub;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "hora_estimada_regreso_club")
    private LocalTime horaEstimadaRegresoClub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", length = 20)
    private TipoActividad tipoActividad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publico_objetivo_id")
    private PublicoObjetivo publicoObjetivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formato_salida_id")
    private FormatoSalida formatoSalida;

    /** Nivel mínimo requerido para inscribirse. Puede ser null si no se restringe. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nivel_minimo_requerido_id")
    private ClasificacionSocio nivelMinimoRequerido;

    @Column(name = "capacidad_maxima")
    private Short capacidadMaxima;

    /** Cuando es {@code true}, el Jefe de Salida ha cerrado inscripciones: no se admiten nuevas ni cancelaciones. */
    @Column(name = "inscripciones_cerradas", nullable = false)
    @Builder.Default
    private boolean inscripcionesCerradas = false;

    @Column(nullable = false, columnDefinition = "estado_salida")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoSalida estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Socio creadoPor;

    @Column(name = "eliminada", nullable = false)
    @Builder.Default
    private boolean eliminada = false;

    @Column(name = "eliminada_en")
    private LocalDateTime eliminadaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eliminada_por_id")
    private Socio eliminadaPor;

    @Column(name = "motivo_eliminacion", columnDefinition = "TEXT")
    private String motivoEliminacion;

    @Column(name = "motivo_cancelacion", columnDefinition = "TEXT")
    private String motivoCancelacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelada_por_id")
    private Socio canceladaPor;

    @Column(name = "cancelada_en")
    private LocalDateTime canceladaEn;

    @Column(name = "jefe_abandono_nombre", length = 200)
    private String jefeAbandonoNombre;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (estado == null) estado = EstadoSalida.PLANIFICADA;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
