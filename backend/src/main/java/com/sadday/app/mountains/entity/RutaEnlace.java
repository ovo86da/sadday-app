package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Enlace externo de una ruta: Wikiloc, GaiaGPS, Komoot, la ficha de un parque…
 *
 * <p>Sustituye a los campos {@code rutas.track_url} y
 * {@code rutas.documentacion_url}, que admitían un único enlace cada uno. La
 * etiqueta es texto libre y no un catálogo cerrado: las apps de navegación
 * aparecen y desaparecen, y un catálogo obligaría a migrar cada vez que el club
 * empiece a usar una nueva.
 */
@Entity
@Table(name = "ruta_enlace")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaEnlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id", nullable = false)
    private Ruta ruta;

    @Column(nullable = false, length = 100)
    private String etiqueta;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(nullable = false)
    private Short orden;
}
