package com.sadday.app.socios.dto;

import java.util.List;

/**
 * Respuesta con todos los valores de las tablas de catálogo usadas en el módulo Socios.
 * Permite a los frontends poblar selectores sin hacer múltiples peticiones.
 */
public record LookupsResponse(
        List<LookupItem<Short>>  tiposSocio,
        List<LookupItem<Short>>  estadosHabilitacion,
        List<LookupItem<Short>>  rolesSistema,
        List<ClasificacionItem>  clasificaciones
) {

    public record LookupItem<T>(T id, String nombre, String descripcion) {}

    public record ClasificacionItem(String id, Short nivel, String nombre, String descripcion) {}
}
