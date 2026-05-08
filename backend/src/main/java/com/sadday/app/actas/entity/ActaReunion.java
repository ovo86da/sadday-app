package com.sadday.app.actas.entity;

import com.sadday.app.shared.entity.Documento;
import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;


/**
 * Acta de reunión del club.
 *
 * <p>Solo la Secretaria (o Admin) puede crear actas.
 * El campo {@code search_vector} es mantenido por un trigger de PostgreSQL
 * y no se mapea en JPA — se usa únicamente en consultas nativas FTS.
 *
 * <p>El PDF exportado se almacena en S3. Los metadatos se guardan en {@link Documento}.
 */
@Entity
@Table(name = "actas_reunion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActaReunion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Tipo de reunión: DIRECTIVA (solo directivos+) o SOCIOS (todos). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_acta", nullable = false)
    private TipoActa tipoActa;

    /** Número secuencial de la reunión (extraído del título del .md). */
    @Column(name = "numero_reunion")
    private Integer numeroReunion;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    /** Hora de cierre de la reunión (presente en el formato .md del club). */
    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Column(length = 200)
    private String lugar;

    @Column(name = "actividades_realizadas_desc", columnDefinition = "TEXT")
    private String actividadesRealizadasDesc;

    @Column(name = "actividades_por_realizar", columnDefinition = "TEXT")
    private String actividadesPorRealizar;

    /** Acuerdos formales extraídos de los "**Acuerdo:**" del desarrollo del acta. */
    @Column(columnDefinition = "TEXT")
    private String acuerdos;

    @Column(columnDefinition = "TEXT")
    private String varios;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creada_por_id", nullable = false)
    private Socio creadaPor;

    /** Quien presidió la reunión (puede diferir del Admin del sistema). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presidente_reunion_id")
    private Socio presidenteReunion;

    /** Quien actuó de secretaria en la reunión (puede diferir del perfil Secretaria del sistema). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secretaria_reunion_id")
    private Socio secretariaReunion;

    /** PDF generado, almacenado en S3. Null hasta que se genere el primer PDF. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    // search_vector (TSVECTOR) es gestionado por trigger de BD — no se mapea en JPA.

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
