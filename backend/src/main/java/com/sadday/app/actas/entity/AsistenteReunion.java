package com.sadday.app.actas.entity;

import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

/**
 * Registro de asistencia a una reunión.
 *
 * <p>{@code socio_id} es nullable: cuando se importa desde un .md y el nombre
 * no puede resolverse automáticamente, se guarda el {@code nombre_raw} tal como
 * aparece en el acta y la secretaria puede asignarlo manualmente después.
 * La unicidad está garantizada en BD mediante índices parciales (V37).
 */
@Entity
@Table(name = "asistentes_reunion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsistenteReunion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acta_id", nullable = false)
    private ActaReunion acta;

    /** Nullable: puede estar sin resolver si el nombre del .md no matcheó un socio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id")
    private Socio socio;

    /** Nombre tal como aparece en el .md; siempre presente cuando se importa desde archivo. */
    @Column(name = "nombre_raw", length = 200)
    private String nombreRaw;
}
