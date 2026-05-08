package com.sadday.app.socios.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Estado de habilitación de un socio (ej. "Habilitado", "Inhabilitado", "Socio Vitalicio").
 * Asignado manualmente por un Directivo.
 */
@Entity
@Table(name = "estado_habilitacion")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoHabilitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;
}
