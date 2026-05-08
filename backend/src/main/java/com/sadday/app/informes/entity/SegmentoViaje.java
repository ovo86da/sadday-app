package com.sadday.app.informes.entity;

import com.sadday.app.mountains.entity.Contacto;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "segmentos_viaje")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SegmentoViaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "informe_salida_id", nullable = false)
    private InformeSalida informe;

    @Column(nullable = false)
    private Short orden;

    @Column(nullable = false, length = 200)
    private String origen;

    @Column(nullable = false, length = 200)
    private String destino;

    @Column(name = "alquilo_transporte", nullable = false)
    private Boolean alquiloTransporte;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_transporte", length = 20)
    private TipoTransporte tipoTransporte;

    @Column(name = "costo_individual", precision = 8, scale = 2)
    private BigDecimal costoIndividual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacto_id")
    private Contacto contacto;
}
