package com.sadday.app.salidas.entity;

import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Inscripción de un socio a una salida.
 *
 * <p>Una fila por socio por salida. Las dignidades (incluyendo "Jefe de Salida") se
 * almacenan en {@code salida_participante_dignidades}.
 *
 * <p>El bloque de campos {@code riesgoAprobadoPor*} registra la aprobación explícita
 * cuando un socio no cumple el nivel mínimo requerido (requiere Directivo + Jefe de Salida).
 */
@Entity
@Table(name = "salida_participantes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalidaParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salida_id", nullable = false)
    private Salida salida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @Column(name = "estado_inscripcion", nullable = false, columnDefinition = "estado_inscripcion")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoInscripcion estadoInscripcion;

    /** Socio Directivo que aprobó el riesgo de inscripción cuando el nivel no cumple el mínimo. */
    @Column(name = "riesgo_aprobado_por_directivo")
    private UUID riesgoAprobadoPorDirectivo;

    /** Jefe de Salida que aprobó el riesgo de inscripción. */
    @Column(name = "riesgo_aprobado_por_jefe")
    private UUID riesgoAprobadoPorJefe;

    @Column(name = "riesgo_aprobado_en")
    private LocalDateTime riesgoAprobadoEn;

    /** Motivo registrado por el Directivo/Admin al aprobar o negar el riesgo. */
    @Column(name = "motivo_directivo", columnDefinition = "TEXT")
    private String motivoDirectivo;

    /** Motivo registrado por el Jefe de Salida al aprobar o negar el riesgo. */
    @Column(name = "motivo_jefe", columnDefinition = "TEXT")
    private String motivoJefe;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (estadoInscripcion == null) estadoInscripcion = EstadoInscripcion.INSCRITO;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
