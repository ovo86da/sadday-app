/// Política de retry para los providers de Riverpod 3.x.
///
/// Por defecto Riverpod reintenta cualquier provider que falla, con backoff
/// exponencial. Eso retrasa que el error sea visible: un error de negocio
/// (ej. 409 "ruta no aprobada") se reintenta varias veces inútilmente antes
/// de mostrarse, porque el resultado no va a cambiar.
///
/// La app ya tiene botones "Reintentar" manuales en sus estados de error,
/// así que desactivamos el retry automático: los errores aparecen al instante.
Duration? noProviderRetry(int retryCount, Object error) => null;
