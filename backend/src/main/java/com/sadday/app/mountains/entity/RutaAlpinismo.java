package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rutas_alpinismo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaAlpinismo {

    @Id
    @Column(name = "ruta_id")
    private Integer rutaId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escala_alpina_ifas_id", nullable = false)
    private EscalaAlpinaIfas escalaAlpinaIfas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dificultad_roca_id", nullable = false)
    private DificultadRoca dificultadRoca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dificultad_hielo_id", nullable = false)
    private DificultadHielo dificultadHielo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compromiso_id", nullable = false)
    private Compromiso compromiso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yosemite_id", nullable = false)
    private SistemaClasesYosemite yosemite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sadday_nivel_tecnico_id", nullable = false)
    private SaddayRiesgoExigencia saddayNivelTecnico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sadday_nivel_fisico_id", nullable = false)
    private SaddayRiesgoExigencia saddayNivelFisico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_montana_id")
    private EquipoMontana equipoMontana;
}
