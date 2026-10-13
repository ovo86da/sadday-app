package com.sadday.app.mountains.entity;

import com.sadday.app.socios.entity.Socio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Un socio avala un documento de ruta.
 *
 * <p>La clave compuesta (documento, socio) es lo que impide confirmar dos veces
 * el mismo fichero. Al reemplazar un track, el {@code ON DELETE CASCADE} de la
 * tabla borra sus confirmaciones: avalaban <em>ese</em> fichero, no a la
 * persona, y nadie ha visto todavía el nuevo.
 */
@Entity
@Table(name = "ruta_documento_confirmacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RutaDocumentoConfirmacion {

    @EmbeddedId
    private RutaDocumentoConfirmacionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("rutaDocumentoId")
    @JoinColumn(name = "ruta_documento_id")
    private RutaDocumento rutaDocumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("socioId")
    @JoinColumn(name = "socio_id")
    private Socio socio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
