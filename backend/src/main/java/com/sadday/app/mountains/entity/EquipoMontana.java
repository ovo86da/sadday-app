package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

/** Tipo de equipo recomendado para una ruta. Seeded en V15. */
@Entity
@Table(name = "equipo_montana")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipoMontana {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;
}
