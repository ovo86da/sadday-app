package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "rutas_ciclismo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaCiclismo {

    @Id
    @Column(name = "ruta_id")
    private Integer rutaId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    /** RIGIDA | DOBLE_SUSPENSION | ENDURO | GRAVEL | RUTA */
    @Column(name = "tipo_bicicleta", nullable = false, length = 25)
    private String tipoBicicleta;

    /** S0 | S1 | S2 | S3 | S4 */
    @Column(name = "dificultad_tecnica", length = 5)
    private String dificultadTecnica;

    @Column(name = "superficie_predominante", length = 200)
    private String superficiePredominante;

    /** Porcentaje de la ruta que se puede pedalear (0–100). */
    @Column(name = "ciclabilidad_pct", precision = 5, scale = 2)
    private BigDecimal ciclabilidadPct;
}
