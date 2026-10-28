package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Escala STC de dificultad técnica para ciclismo de montaña (S0–S4).
 *
 * <p>Existe desde FR-028. Antes la dificultad de ciclismo era un
 * {@code varchar(5)} con un CHECK y sin orden explícito, y comparar necesita
 * ese orden: deducirlo del texto funciona hasta que alguien añade un grado que
 * no encaja con el alfabeto.
 *
 * <p>Los ids son los mismos valores que ya guarda
 * {@code rutas_ciclismo.dificultad_tecnica}, así que esa columna sigue
 * funcionando sin tocarla.
 */
@Entity
@Table(name = "dificultad_ciclismo_stc")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DificultadCiclismoStc {

    @Id
    @Column(length = 5)
    private String id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false)
    private Short rank;
}
