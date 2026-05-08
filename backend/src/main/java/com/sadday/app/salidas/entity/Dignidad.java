package com.sadday.app.salidas.entity;

import jakarta.persistence.*;
import lombok.*;

/** Dignidad contextual de un socio dentro de una salida (Jefe de Salida, Guía, Cronista…). Seeded en V8. */
@Entity
@Table(name = "dignidades")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dignidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;
}
