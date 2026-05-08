package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "compromiso")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compromiso {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Short rank;
}
