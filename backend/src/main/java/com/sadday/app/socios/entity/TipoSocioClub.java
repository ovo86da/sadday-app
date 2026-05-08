package com.sadday.app.socios.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tipo de socio en el club (ej. "Socio Activo", "Aspirante", "Juvenil", "Ex-socio", etc.).
 * Tabla de catálogo inmutable en tiempo de ejecución normal.
 */
@Entity
@Table(name = "tipo_socio_club")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoSocioClub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;
}
