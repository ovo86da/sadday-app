package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dificultad_hielo_wi")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DificultadHielo {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false, length = 10)
    private String grado;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Short rank;
}
