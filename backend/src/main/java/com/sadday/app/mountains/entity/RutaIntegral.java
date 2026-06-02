package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rutas_integral")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaIntegral {

    @Id
    @Column(name = "ruta_id")
    private Integer rutaId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @Column(name = "dificultad_max_tipo", length = 20)
    private String dificultadMaxTipo;

    @Column(name = "dificultad_maxima_descripcion", length = 200)
    private String dificultadMaximaDescripcion;

    @Column(name = "descripcion_itinerario", columnDefinition = "TEXT")
    private String descripcionItinerario;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ruta_id")
    @OrderBy("id.secuencia ASC")
    @Builder.Default
    private List<RutaCumbre> cumbres = new ArrayList<>();
}
