package com.sadday.app.socios.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Nivel técnico/clasificación de un socio como montañero.
 * Ejemplo: "SO001" = Externo, "SO002" = Principiante, ..., "SO006" = Expert.
 * El nivel 5 (Juvenil lógico) se calcula automáticamente desde fecha_nacimiento.
 */
@Entity
@Table(name = "clasificacion_socio")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClasificacionSocio {

    /** VARCHAR(10) PK, ej: "SO001", "SO002", ..., "SO006". */
    @Id
    @Column(length = 10)
    private String id;

    /** Nivel ordinal: 0=Externo, 1=Principiante, ..., 5=Expert. */
    @Column(nullable = false, unique = true)
    private Short nivel;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;
}
