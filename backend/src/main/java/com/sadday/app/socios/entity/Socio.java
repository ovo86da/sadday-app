package com.sadday.app.socios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad principal del socio del club.
 *
 * <p>Separada de los datos de autenticación ({@code usuarios_auth}) por principio
 * de separación de responsabilidades y seguridad.
 *
 * <p>El campo {@code edad} NO se almacena en BD — se calcula siempre
 * desde {@code fechaNacimiento}.
 *
 * <p>El tipo "Juvenil" es asignado automáticamente por un {@code @Scheduled}
 * diario que compara {@code fechaNacimiento} con la fecha actual.
 */
@Entity
@Table(name = "socios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Socio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, unique = true, length = 20)
    private String cedula;

    @Column(nullable = false, unique = true, length = 255)
    private String correo;

    @Column(length = 20)
    private String telefono;

    @Column(columnDefinition = "TEXT")
    private String direccion;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "fecha_salida")
    private LocalDate fechaSalida;

    @Column(name = "tipo_sangre", length = 5)
    private String tipoSangre;

    // Contacto de emergencia 1
    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_direccion", columnDefinition = "TEXT")
    private String emergencyContactDireccion;

    // Contacto de emergencia 2
    @Column(name = "emergency_contact_name2", length = 200)
    private String emergencyContactName2;

    @Column(name = "emergency_contact_phone2", length = 20)
    private String emergencyContactPhone2;

    @Column(name = "emergency_contact_direccion2", columnDefinition = "TEXT")
    private String emergencyContactDireccion2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_habilitacion_id", nullable = false)
    private EstadoHabilitacion estadoHabilitacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_socio_id", nullable = false)
    private TipoSocioClub tipoSocio;

    /** Estado de acceso al sistema (login). Solo ACTIVE permite iniciar sesión. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_acceso_id", nullable = false)
    private EstadoAcceso estadoAcceso;

    /** Nivel técnico como montañero. Null para socios sin clasificación asignada aún. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nivel_tecnico_id")
    private ClasificacionSocio nivelTecnico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_sistema_id", nullable = false)
    private RolSistema rolSistema;

    /** Solo aplica a DIRECTIVO. Cuando true, puede aprobar/negar inscripciones con nivel insuficiente. */
    @Column(name = "es_jefe_montana", nullable = false)
    @Builder.Default
    private boolean esJefeMontana = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (fechaIngreso == null) {
            fechaIngreso = LocalDate.now();
        }
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Calcula la edad actual en años a partir de {@code fechaNacimiento}. */
    public int calcularEdad() {
        return LocalDate.now().getYear() - fechaNacimiento.getYear()
                - (LocalDate.now().getDayOfYear() < fechaNacimiento.getDayOfYear() ? 1 : 0);
    }

    /** Calcula la antigüedad en años a partir de {@code fechaIngreso}. */
    public int calcularAntiguedad() {
        if (fechaIngreso == null) return 0;
        return LocalDate.now().getYear() - fechaIngreso.getYear()
                - (LocalDate.now().getDayOfYear() < fechaIngreso.getDayOfYear() ? 1 : 0);
    }
}
