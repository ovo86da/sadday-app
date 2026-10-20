// GENERADO AUTOMÁTICAMENTE — no editar a mano.
// 
// Fuente: shared/umbrales.json
// Regenerar: node scripts/generar-umbrales.mjs

/// Movimiento mínimo para que un tramo cuente como distancia, en metros. En
/// crudo la distancia sale un 4-5 % larga: la geometría es exacta, pero se
/// aplica sobre puntos con ruido y cada temblor del GPS añade longitud.
/// Calibrado contra las referencias del club: el error baja a menos del 0,5
/// %.
const double minimoMovimientoM = 12.0;

/// Cambio mínimo de altitud para que cuente como desnivel, en metros. No se
/// puede clavar: en crudo infla entre un 21 % y un 48 %, y el valor que
/// acierta depende del perfil (30 m en una ruta de subida y bajada, 70 m en
/// una ondulada de 23 km). 40 m es el compromiso, con un error del -1 % al
/// +4 %. Por eso el resultado se presenta como estimación.
const double umbralDesnivelM = 40.0;

/// Tope de puntos a recorrer en el navegador; por encima se muestrea. Un
/// GPX de 10 MB son decenas de miles de puntos y el parseo no debe bloquear
/// la interfaz.
const int maximoPuntosWeb = 20000;

/// Tope de puntos en el móvil. Más bajo que el de la web porque un teléfono
/// tiene menos margen. PENDIENTE: fijado por prudencia, sin medir en un
/// dispositivo real.
const int maximoPuntosMovil = 8000;
