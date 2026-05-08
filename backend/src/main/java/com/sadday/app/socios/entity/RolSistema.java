package com.sadday.app.socios.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Rol del sistema de un socio (Admin, Secretaria, Directivo, Socio).
 * 4 roles globales; "Jefe de Salida" es contextual por salida, no un rol global.
 */
@Entity
@Table(name = "roles_sistema")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;
}
