package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sistema_clases_yosemite")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SistemaClasesYosemite {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Short rank;
}
