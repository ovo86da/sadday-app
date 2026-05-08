package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dificultad_roca_uiaa_francesa")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DificultadRoca {

    @Id
    @Column(length = 15)
    private String id;

    @Column(length = 10)
    private String uiaa;

    @Column(length = 10)
    private String francesa;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Short rank;
}
