import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/features/rutas/domain/models/ruta_model.dart';

void main() {
  Map<String, dynamic> json({Map<String, dynamic> extra = const {}}) => {
        'id': 'd1',
        'tipo': 'TRACK_GPX',
        'filename': 'ruta.gpx',
        'sizeBytes': 2048,
        'confirmaciones': 3,
        'confirmadoPorMi': true,
        'puedoEliminar': true,
        'subidoPorId': 's1',
        'subidoPorNombre': 'Luis Torres',
        ...extra,
      };

  group('RutaDocumento', () {
    test('lee los campos del servidor', () {
      final d = RutaDocumento.fromJson(json());
      expect(d.id, 'd1');
      expect(d.confirmaciones, 3);
      expect(d.confirmadoPorMi, isTrue);
      expect(d.puedoEliminar, isTrue);
      expect(d.subidoPorId, 's1');
    });

    test('si el servidor no manda puedoEliminar, NO se puede eliminar', () {
      // Falla cerrado a proposito. Si un cambio de contrato deja de mandar ese
      // campo, el boton desaparece en vez de aparecer para todo el mundo. El
      // permiso de verdad lo comprueba el endpoint, pero un boton que no
      // deberia estar ahi es una invitacion a intentarlo.
      final d = RutaDocumento.fromJson(json()..remove('puedoEliminar'));
      expect(d.puedoEliminar, isFalse);
    });

    test('si no manda confirmadoPorMi, se asume que no', () {
      final d = RutaDocumento.fromJson(json()..remove('confirmadoPorMi'));
      expect(d.confirmadoPorMi, isFalse);
      expect(d.confirmaciones, 3, reason: 'el total sigue siendo del servidor');
    });

    test('un tipo desconocido cae en «Otros» en vez de romper', () {
      // Si el backend añade un tipo nuevo, el movil viejo tiene que seguir
      // mostrando el documento, no esconderlo.
      final d = RutaDocumento.fromJson(json(extra: {'tipo': 'INVENTADO'}));
      expect(d.tipo, 'INVENTADO');
      expect(RutaDocumento.etiquetaTipo(d.tipo), 'Otros documentos');
    });

    test('el tamano se muestra en KB o MB segun cuanto pese', () {
      expect(RutaDocumento.fromJson(json(extra: {'sizeBytes': 2048})).tamanoLegible, '2 KB');
      expect(RutaDocumento.fromJson(json(extra: {'sizeBytes': 3 * 1024 * 1024})).tamanoLegible, '3.0 MB');
    });

    test('los limites por tipo son los mismos que en el backend', () {
      // 1 track, 3 mapas, 3 permisos, 3 otros. Si el backend los cambia, este
      // test no lo detecta —son dos sitios— pero al menos fija el contrato que
      // el movil cree.
      expect(RutaDocumento.limitePorTipo['TRACK_GPX'], 1);
      expect(RutaDocumento.limitePorTipo['MAPA'], 3);
      expect(RutaDocumento.limitePorTipo['PERMISO'], 3);
      expect(RutaDocumento.limitePorTipo['OTRO'], 3);
    });
  });

  group('Ruta.fromJson', () {
    test('lee la lista de documentos', () {
      final r = Ruta.fromJson({
        'id': 1, 'nombre': 'Ruta', 'documentos': [json()],
      });
      expect(r.documentos, hasLength(1));
      expect(r.documentos.first.filename, 'ruta.gpx');
    });

    test('sin documentos la lista llega vacia, no nula', () {
      // El listado de rutas no incluye `documentos`, solo el detalle. Si esto
      // fuera null, las tarjetas del listado reventarian.
      final r = Ruta.fromJson({'id': 1, 'nombre': 'Ruta'});
      expect(r.documentos, isEmpty);
    });
  });
}
