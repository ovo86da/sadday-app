package com.sadday.app.mountains.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RutaCumbreId implements Serializable {
    private Integer rutaId;
    private Short secuencia;
}
