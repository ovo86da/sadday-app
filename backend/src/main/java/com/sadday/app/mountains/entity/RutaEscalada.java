package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rutas_escalada")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaEscalada {

    @Id
    @Column(name = "ruta_id")
    private Integer rutaId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dificultad_roca_id", nullable = false)
    private DificultadRoca dificultadRoca;

    /** DEPORTIVA | TRADICIONAL | MIXTA | BOULDER */
    @Column(name = "tipo_escalada", nullable = false, length = 20)
    private String tipoEscalada;

    @Column(name = "num_cintas")
    private Short numCintas;

    @Column(name = "altura_via_m")
    private Integer alturaViaM;

    @Column(name = "tipo_roca", length = 100)
    private String tipoRoca;
}
