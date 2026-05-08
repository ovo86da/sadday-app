package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "escala_alpina_ifas")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscalaAlpinaIfas {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false, length = 10)
    private String grado;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Short rank;
}
