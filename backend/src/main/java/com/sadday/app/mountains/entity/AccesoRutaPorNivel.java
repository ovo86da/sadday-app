package com.sadday.app.mountains.entity;

import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Umbrales máximos de dificultad permitidos para cada nivel de socio.
 *
 * <p>Editable por Directivos y Admins. Cada cambio se audita.
 * Seeded inicialmente en V8 para los 6 niveles de clasificación.
 */
@Entity
@Table(name = "acceso_ruta_por_nivel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccesoRutaPorNivel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nivel_socio_id", nullable = false, unique = true)
    private ClasificacionSocio nivelSocio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "max_ifas_id", nullable = false)
    private EscalaAlpinaIfas maxIfas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "max_roca_id", nullable = false)
    private DificultadRoca maxRoca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "max_hielo_id", nullable = false)
    private DificultadHielo maxHielo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "max_compromiso_id", nullable = false)
    private Compromiso maxCompromiso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "max_yosemite_id", nullable = false)
    private SistemaClasesYosemite maxYosemite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "max_sadday_tecnico_id", nullable = false)
    private SaddayRiesgoExigencia maxSaddayTecnico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "max_sadday_fisico_id", nullable = false)
    private SaddayRiesgoExigencia maxSaddayFisico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private Socio updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
