package com.sadday.app.salidas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "publico_objetivo")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicoObjetivo {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false)
    private Short orden;
}
