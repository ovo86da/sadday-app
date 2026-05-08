package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dificultad_senderismo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DificultadSenderismo {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Short rank;
}
