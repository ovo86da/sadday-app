package com.sadday.app.salidas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "formato_salida")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormatoSalida {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false)
    private Short orden;
}
