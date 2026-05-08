package com.sadday.app.auth.service;

/**
 * Detecta la plataforma de origen a partir del User-Agent.
 *
 * <p>Criterio: se considera MOBILE cuando el UA corresponde a un cliente
 * nativo (Flutter/Dart, OkHttp, CFNetwork, o app móvil sin cabecera de
 * navegador de escritorio). Todo lo demás es WEB.
 */
public final class PlatformDetector {

    public static final String WEB    = "WEB";
    public static final String MOBILE = "MOBILE";

    private PlatformDetector() {}

    public static String detect(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return WEB;
        String ua = userAgent.toLowerCase();
        if (ua.contains("okhttp")
                || ua.contains("dart")
                || ua.contains("flutter")
                || ua.contains("cfnetwork")
                || ua.contains("nsurlsession")) {
            return MOBILE;
        }
        return WEB;
    }
}
