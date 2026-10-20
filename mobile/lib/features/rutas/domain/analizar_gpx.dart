import 'package:xml/xml.dart';
import 'dart:math' as math;

import 'umbrales.g.dart';

/// Analiza un GPX y saca los datos de la ruta (FR-023, portado en FR-025).
///
/// Es la **segunda** implementacion de estas formulas: la primera esta en
/// `frontend/src/lib/analizar-gpx.ts`. Se repite en vez de llamar a un endpoint
/// porque en el flujo de proponer ruta el fichero todavia no se ha subido, y
/// mandar hasta 10 MB por datos moviles solo para ver unos numeros no tiene
/// sentido; en montaña, ademas, puede no haber cobertura.
///
/// El riesgo de tener la formula dos veces es que se desincronicen. Eso lo
/// vigila la suite de conformidad de `test-fixtures/gpx/`, que las dos
/// implementaciones tienen que satisfacer: si alguien cambia un umbral aqui y
/// no alli, falla la CI en vez de fallar en silencio.
///
/// **Si cambias un numero de este fichero, cambialo tambien en el de TypeScript.**

// Los umbrales no se escriben aqui: vienen de shared/umbrales.json a traves de
// umbrales.g.dart. La misma formula esta implementada en TypeScript, y dos
// constantes con el mismo significado y ningun vinculo entre ellas se
// desincronizan sin que nadie lo note.
//
// Se usa `maximoPuntosMovil`, mas bajo que el de la web porque un telefono
// tiene menos margen.

class DatosDelTrack {
  const DatosDelTrack({
    required this.distanciaKm,
    required this.desnivelPositivoM,
    required this.desnivelNegativoM,
    required this.duracionHoras,
    required this.duracionDias,
    required this.altitudMinimaM,
    required this.altitudMaximaM,
    required this.puntos,
    required this.muestreado,
  });

  /// Kilometros, un decimal.
  final double distanciaKm;

  /// Metros, redondeado a la decena.
  final int desnivelPositivoM;

  /// Metros, redondeado a la decena.
  final int desnivelNegativoM;

  /// Horas con un decimal, o `null` si el GPX no trae marcas de tiempo.
  final double? duracionHoras;

  /// Dias completos cuando el track pasa de 24 h, si no `null`.
  final int? duracionDias;

  final int? altitudMinimaM;
  final int? altitudMaximaM;
  final int puntos;

  /// True si se muestreo por exceder [maximoPuntosMovil].
  final bool muestreado;
}

class _Punto {
  const _Punto(this.lat, this.lon, this.ele, this.t);
  final double lat;
  final double lon;
  final double? ele;
  final DateTime? t;
}

/// Distancia entre dos puntos sobre la esfera, en metros.
double _haversine(_Punto a, _Punto b) {
  const r = 6371000.0;
  final f1 = a.lat * math.pi / 180;
  final f2 = b.lat * math.pi / 180;
  final df = f2 - f1;
  final dl = (b.lon - a.lon) * math.pi / 180;
  final sdf = math.sin(df / 2);
  final sdl = math.sin(dl / 2);
  final h = sdf * sdf + math.cos(f1) * math.cos(f2) * sdl * sdl;
  return 2 * r * math.asin(math.sqrt(h));
}

/// Distancia acumulada descartando los movimientos menores de 12 m.
///
/// No es un suavizado: se compara contra el ultimo punto que SI conto, no
/// contra el anterior, asi que un tramo lento no se pierde, solo se agrupa.
double _distanciaM(List<_Punto> puntos) {
  if (puntos.length < 2) return 0;
  var total = 0.0;
  var referencia = puntos.first;
  for (final p in puntos.skip(1)) {
    final d = _haversine(referencia, p);
    if (d >= minimoMovimientoM) {
      total += d;
      referencia = p;
    }
  }
  return total;
}

/// Desnivel de subida y de bajada, con histeresis de 40 m.
///
/// El umbral no se puede clavar: en crudo infla entre un 21 % y un 48 %, y el
/// que acierta depende del perfil. 40 m es el compromiso calibrado contra los
/// tracks del club. Por eso el resultado se presenta como estimacion.
({double subida, double bajada}) _desnivelesM(List<_Punto> puntos) {
  var subida = 0.0;
  var bajada = 0.0;
  double? base;
  for (final p in puntos) {
    final ele = p.ele;
    if (ele == null) continue;
    if (base == null) {
      base = ele;
      continue;
    }
    final d = ele - base;
    if (d >= umbralDesnivelM) {
      subida += d;
      base = ele;
    } else if (-d >= umbralDesnivelM) {
      bajada += -d;
      base = ele;
    }
  }
  return (subida: subida, bajada: bajada);
}

/// Redondea a la decena: dar «1112 m» sugiere una exactitud que no existe.
int _aDecenas(double m) => (m / 10).round() * 10;

/// Analiza el contenido de un GPX.
///
/// Devuelve `null` si no hay puntos que medir, que es como se distingue «este
/// fichero no sirve» de «este fichero da ceros».
DatosDelTrack? analizarGpx(String contenido) {
  final XmlDocument doc;
  try {
    doc = XmlDocument.parse(contenido);
  } on XmlException {
    return null;
  }

  var puntos = doc
      .findAllElements('trkpt')
      .map((n) {
        final lat = double.tryParse(n.getAttribute('lat') ?? '');
        final lon = double.tryParse(n.getAttribute('lon') ?? '');
        final eleTxt = n.getElement('ele')?.innerText.trim();
        final tTxt = n.getElement('time')?.innerText.trim();
        return _Punto(
          lat ?? double.nan,
          lon ?? double.nan,
          (eleTxt == null || eleTxt.isEmpty) ? null : double.tryParse(eleTxt),
          (tTxt == null || tTxt.isEmpty) ? null : DateTime.tryParse(tTxt),
        );
      })
      .where((p) => !p.lat.isNaN && !p.lon.isNaN)
      .toList();

  if (puntos.length < 2) return null;

  final totales = puntos.length;
  final muestreado = totales > maximoPuntosMovil;
  if (muestreado) {
    // Se conservan el primero y el ultimo: perder un extremo falsearia la
    // distancia y el desnivel justo donde mas se nota.
    final paso = (totales / maximoPuntosMovil).ceil();
    final reducidos = <_Punto>[
      for (var i = 0; i < totales; i += paso) puntos[i],
    ];
    if (reducidos.last != puntos.last) reducidos.add(puntos.last);
    puntos = reducidos;
  }

  final desniveles = _desnivelesM(puntos);
  final alturas = puntos.map((p) => p.ele).whereType<double>().toList();
  final tiempos = puntos.map((p) => p.t).whereType<DateTime>().toList();

  // El tiempo total, no el tiempo en movimiento: un descanso en montaña forma
  // parte de lo que hay que planificar.
  double? duracionHoras;
  int? duracionDias;
  if (tiempos.length >= 2) {
    tiempos.sort();
    final horas =
        tiempos.last.difference(tiempos.first).inSeconds / 3600.0;
    if (horas >= 24) {
      // Dias y horas son campos distintos; «1 dia y 12 horas» no cabe en uno.
      duracionDias = (horas / 24).ceil();
    } else {
      duracionHoras = (horas * 10).round() / 10;
    }
  }

  return DatosDelTrack(
    distanciaKm: ((_distanciaM(puntos) / 1000) * 10).round() / 10,
    desnivelPositivoM: _aDecenas(desniveles.subida),
    desnivelNegativoM: _aDecenas(desniveles.bajada),
    duracionHoras: duracionHoras,
    duracionDias: duracionDias,
    altitudMinimaM: alturas.isEmpty ? null : alturas.reduce(math.min).round(),
    altitudMaximaM: alturas.isEmpty ? null : alturas.reduce(math.max).round(),
    puntos: totales,
    muestreado: muestreado,
  );
}
