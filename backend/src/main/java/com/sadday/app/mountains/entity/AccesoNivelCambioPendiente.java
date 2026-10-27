package com.sadday.app.mountains.entity;

import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un cambio de umbrales esperando la aprobación de un segundo directivo (FR-026).
 *
 * <p>Cambiar {@link AccesoRutaPorNivel} no es un permiso administrativo
 * cualquiera: decide a qué dificultad puede inscribirse cada nivel de socio. Si
 * alguien relaja un límite, socios que antes no podían ir a cierta ruta de
 * pronto pueden, y la auditoría lo cuenta <em>después</em>. Por eso el cambio
 * se queda aquí y <strong>no toca la tabla vigente</strong> hasta que otro
 * directivo lo aprueba.
 *
 * <p>Se guardan los <strong>siete</strong> valores propuestos aunque solo
 * cambie uno. La unidad que maneja la API, y la que ve quien edita, es la fila
 * entera de un nivel; guardar solo lo que cambia obligaría a reconstruir el
 * resto al aprobar, contra un presente que puede haberse movido mientras tanto.
 */
@Entity
@Table(name = "acceso_nivel_cambio_pendiente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccesoNivelCambioPendiente {

    /** Estados posibles. Replicados en el {@code chk_estado_cambio} de V22. */
    public enum Estado { PENDIENTE, APROBADO, RECHAZADO, CANCELADO }

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nivel_socio_id", nullable = false)
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
    @JoinColumn(name = "solicitado_por_id", nullable = false)
    private Socio solicitadoPor;

    @Column(name = "solicitado_en", nullable = false)
    private LocalDateTime solicitadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private Estado estado;

    /**
     * Quién lo aprobó, rechazó o canceló. Nunca <em>qué cargo</em> podía
     * hacerlo: esa regla vive en el servicio y va a cambiar, así que guardarla
     * aquí obligaría a migrar las filas viejas cada vez que cambie.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por_id")
    private Socio resueltoPor;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(name = "motivo_rechazo", columnDefinition = "text")
    private String motivoRechazo;

    public boolean estaPendiente() {
        return estado == Estado.PENDIENTE;
    }
}
