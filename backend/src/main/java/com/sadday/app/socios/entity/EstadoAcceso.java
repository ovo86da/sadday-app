package com.sadday.app.socios.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Estado de acceso al sistema de un socio.
 * Controla si puede iniciar sesión — independiente del estado de negocio ({@code tipoSocio}).
 *
 * <p>Valores: ACTIVE, BLOCKED, EX_MEMBER, PENDING_REGISTER, DISABLED.
 * Solo {@code ACTIVE} permite el login.
 */
@Entity
@Table(name = "estado_acceso")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    /** Clave programática inmutable. Ej: "ACTIVE", "BLOCKED". */
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;
}
