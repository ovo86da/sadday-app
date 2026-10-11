package com.sadday.app.socios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
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

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "fecha_salida")
    private LocalDate fechaSalida;

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

    /** Presidenta del club. Solo una persona puede tener este flag activo a la vez. */
    @Column(name = "es_presidenta", nullable = false)
    @Builder.Default
    private boolean esPresidenta = false;

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

    /** Calcula la edad actual en años. Retorna null si fechaNacimiento aún no fue informada. */
    public Integer calcularEdad() {
        return calcularEdad(LocalDate.now());
    }

    /**
     * Edad en años cumplidos a la fecha dada.
     *
     * <p>La sobrecarga con fecha existe para poder probar los límites —el día
     * del cumpleaños, el día anterior, los años bisiestos— sin depender del
     * reloj de la máquina.
     *
     * <p>Usa {@link Period}, no aritmética sobre el día del año. La versión
     * anterior restaba {@code getDayOfYear()}, y el día del año se desplaza en
     * los bisiestos: quien nació en año bisiesto a partir del 1 de marzo salía
     * con un año menos del que le correspondía, desde su cumpleaños hasta fin
     * de año.
     */
    public Integer calcularEdad(LocalDate hoy) {
        if (fechaNacimiento == null) return null;
        return Period.between(fechaNacimiento, hoy).getYears();
    }

    /** Calcula la antigüedad en años a partir de {@code fechaIngreso}. */
    public int calcularAntiguedad() {
        return calcularAntiguedad(LocalDate.now());
    }

    /** Antigüedad en años cumplidos a la fecha dada. Mismo arreglo que {@link #calcularEdad(LocalDate)}. */
    public int calcularAntiguedad(LocalDate hoy) {
        if (fechaIngreso == null) return 0;
        return Period.between(fechaIngreso, hoy).getYears();
    }
}
