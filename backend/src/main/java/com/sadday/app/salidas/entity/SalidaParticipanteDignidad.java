package com.sadday.app.salidas.entity;

import jakarta.persistence.*;
import lombok.*;

/** Dignidad asignada a un participante en el contexto de una salida específica. */
@Entity
@Table(name = "salida_participante_dignidades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalidaParticipanteDignidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participante_id", nullable = false)
    private SalidaParticipante participante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dignidad_id", nullable = false)
    private Dignidad dignidad;
}
