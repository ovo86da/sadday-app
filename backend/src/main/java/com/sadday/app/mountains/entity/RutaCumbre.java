package com.sadday.app.mountains.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ruta_cumbres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaCumbre {

    @EmbeddedId
    private RutaCumbreId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("rutaId")
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mountain_id", nullable = false)
    private Mountain mountain;
}
