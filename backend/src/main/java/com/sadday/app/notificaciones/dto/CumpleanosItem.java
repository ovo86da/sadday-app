package com.sadday.app.notificaciones.dto;


import java.util.UUID;

public record CumpleanosItem(
        UUID socioId,
        String nombre,
        String apellido,
        /** Edad que cumple hoy. */
        int edad
) {}
