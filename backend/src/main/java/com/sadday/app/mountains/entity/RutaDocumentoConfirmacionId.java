package com.sadday.app.mountains.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RutaDocumentoConfirmacionId implements Serializable {
    private UUID rutaDocumentoId;
    private UUID socioId;
}
