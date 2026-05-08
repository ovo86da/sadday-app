package com.sadday.app.salidas.entity;

/** Estado del ciclo de vida de una salida. Mapeado al ENUM PostgreSQL {@code estado_salida}. */
public enum EstadoSalida {
    PLANIFICADA,
    EN_CURSO,
    REALIZADA,
    CANCELADA
}
