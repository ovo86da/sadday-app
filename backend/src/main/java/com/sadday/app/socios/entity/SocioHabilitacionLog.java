package com.sadday.app.socios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Registro de auditoría de cada cambio de estado de habilitación de un socio.
 *
 * <p>Se crea una entrada por cada vez que se habilita o inhabilita un socio,
 * ya sea de forma individual (fuente=MANUAL) o mediante carga masiva de CSV (fuente=CSV).
 */
@Entity
@Table(name = "socio_habilitacion_log")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocioHabilitacionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_anterior_id", nullable = false)
    private EstadoHabilitacion estadoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_nuevo_id", nullable = false)
    private EstadoHabilitacion estadoNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cambiado_por_id", nullable = false)
    private Socio cambiadoPor;

    @Column(name = "cambiado_en", nullable = false)
    private OffsetDateTime cambiadoEn;

    /** 'MANUAL' para cambios individuales; 'CSV' para cargas masivas. */
    @Column(nullable = false, length = 10)
    private String fuente;

    /** Clave en S3 del CSV que originó el cambio (solo cuando fuente = 'CSV'). */
    @Column(name = "csv_key")
    private String csvKey;

    /** Información adicional: fila del CSV, nombre del archivo, etc. */
    @Column
    private String notas;
}
