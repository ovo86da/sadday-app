package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sadday_riesgo_exigencia")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaddayRiesgoExigencia {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false)
    private Short valor;

    @Column(nullable = false, length = 20)
    private String escala;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Short rank;
}
