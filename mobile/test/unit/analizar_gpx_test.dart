import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/features/rutas/domain/analizar_gpx.dart';

/// Suite de conformidad compartida con la web.
///
/// Los ficheros y los valores esperados viven en `test-fixtures/gpx/`, fuera de
/// este paquete, y los lee tambien
/// `frontend/src/lib/analizar-gpx.conformidad.test.ts`. La formula de FR-023
/// esta implementada dos veces —TypeScript y Dart— y el riesgo es que se
/// desincronicen sin que nadie lo note: la web y el movil darian desniveles
/// distintos para el mismo fichero.
///
/// Si este test falla tras tocar un umbral, es que se cambio en un solo lado.
void main() {
  final dir = Directory('../test-fixtures/gpx');

  test('los ficheros de conformidad estan donde se espera', () {
    // Si la carpeta se mueve o se borra, este test lo dice. Sin esto, un
    // `for` sobre una lista vacia pasaria en verde sin comprobar nada, que es
    // el modo de fallo que mas cuesta detectar.
    expect(dir.existsSync(), isTrue,
        reason: 'falta ${dir.absolute.path}');
    expect(File('${dir.path}/esperado.json').existsSync(), isTrue);
  });

  final esperado = jsonDecode(
    File('${dir.path}/esperado.json').readAsStringSync(),
  ) as Map<String, dynamic>;

  test('hay un fichero .gpx por cada entrada de esperado.json', () {
    final ficheros = dir
        .listSync()
        .whereType<File>()
        .where((f) => f.path.endsWith('.gpx'))
        .map((f) => f.uri.pathSegments.last.replaceAll('.gpx', ''))
        .toSet();
    expect(ficheros, equals(esperado.keys.toSet()),
        reason: 'un fichero sin valores esperados no prueba nada, y una '
            'entrada sin fichero no se comprueba nunca');
  });

  for (final nombre in esperado.keys) {
    test('conformidad: $nombre', () {
      final contenido = File('${dir.path}/$nombre.gpx').readAsStringSync();
      final r = analizarGpx(contenido);
      expect(r, isNotNull, reason: 'el analizador devolvio null');

      final e = esperado[nombre] as Map<String, dynamic>;
      expect(r!.distanciaKm, closeTo((e['distanciaKm'] as num).toDouble(), 0.05),
          reason: 'distancia');
      expect(r.desnivelPositivoM, e['desnivelPositivoM'], reason: 'desnivel +');
      expect(r.desnivelNegativoM, e['desnivelNegativoM'], reason: 'desnivel -');
      expect(r.duracionDias, e['duracionDias'], reason: 'duracion en dias');
      expect(r.altitudMinimaM, e['altitudMinimaM'], reason: 'altitud minima');
      expect(r.altitudMaximaM, e['altitudMaximaM'], reason: 'altitud maxima');
      expect(r.puntos, e['puntos'], reason: 'numero de puntos');

      final horas = e['duracionHoras'];
      if (horas == null) {
        expect(r.duracionHoras, isNull, reason: 'duracion en horas');
      } else {
        expect(r.duracionHoras, closeTo((horas as num).toDouble(), 0.05),
            reason: 'duracion en horas');
      }
    });
  }

  group('casos que no dependen de los ficheros', () {
    test('devuelve null con un XML roto', () {
      expect(analizarGpx('<gpx><trk>'), isNull);
    });

    test('devuelve null con menos de dos puntos', () {
      expect(
        analizarGpx('<gpx><trk><trkseg>'
            '<trkpt lat="0.1" lon="-78.2"><ele>3000</ele></trkpt>'
            '</trkseg></trk></gpx>'),
        isNull,
      );
    });
  });
}
