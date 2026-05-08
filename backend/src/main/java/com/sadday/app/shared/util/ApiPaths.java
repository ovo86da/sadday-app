package com.sadday.app.shared.util;

/**
 * Constantes de rutas de la API.
 * Todos los endpoints usan el prefijo /api/v1.
 */
public final class ApiPaths {

    public static final String V1 = "/api/v1";

    // Auth
    public static final String AUTH          = V1 + "/auth";
    public static final String AUTH_LOGIN    = AUTH + "/login";
    public static final String AUTH_REFRESH  = AUTH + "/refresh";
    public static final String AUTH_LOGOUT   = AUTH + "/logout";
    public static final String AUTH_MFA      = AUTH + "/mfa";
    public static final String AUTH_FORGOT   = AUTH + "/forgot-password";
    public static final String AUTH_RESET    = AUTH + "/reset-password";

    // Registro inicial (completar perfil desde link de email)
    public static final String REGISTRO = V1 + "/registro";

    // Módulos de negocio
    public static final String SOCIOS    = V1 + "/socios";
    public static final String MOUNTAINS = V1 + "/mountains";
    public static final String RUTAS     = V1 + "/rutas";
    public static final String SALIDAS   = V1 + "/salidas";
    public static final String INFORMES      = V1 + "/informes";
    public static final String ACTAS         = V1 + "/actas";
    public static final String ESTADISTICAS     = V1 + "/estadisticas";
    public static final String NOTIFICACIONES   = V1 + "/notificaciones";
    public static final String PLANIFICADOR     = V1 + "/planificador";

    // Contactos globales (guías, transportistas, refugios, etc.)
    public static final String CONTACTOS = V1 + "/contactos";

    // Perfil del usuario autenticado
    public static final String PROFILE = V1 + "/profile";

    // Portal de administración (Admin + Secretaria)
    public static final String ADMIN = V1 + "/admin";

    private ApiPaths() {}
}
