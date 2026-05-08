package com.sadday.app.actas.entity;

import com.sadday.app.informes.entity.InformeSalida;
import jakarta.persistence.*;
import lombok.*;

/**
 * Relación many-to-many entre {@link ActaReunion} e {@link InformeSalida}.
 * Un acta puede referenciar varios informes de salidas tratadas en la reunión.
 */
@Entity
@Table(name = "acta_informes_salida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActaInformeSalida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acta_id", nullable = false)
    private ActaReunion acta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "informe_id", nullable = false)
    private InformeSalida informe;
}
