package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rutas_trekking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaTrekking {

    @Id
    @Column(name = "ruta_id")
    private Integer rutaId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dificultad_id", nullable = false)
    private DificultadSenderismo dificultad;

    @Column(name = "es_circular", nullable = false)
    private Boolean esCircular;

    @Column(name = "fuentes_agua", nullable = false)
    private Boolean fuentesAgua;

    @Column(name = "tipo_terreno", length = 200)
    private String tipoTerreno;
}
