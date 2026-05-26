import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/features/salidas/domain/models/salida_model.dart';

const _baseJson = {
  'id': 'a1b2c3d4-0000-0000-0000-000000000042',
  'nombre': 'Cerro Colorado',
  'estado': 'PLANIFICADA',
  'fechaInicio': '2025-06-01',
  'capacidadMaxima': 20,
  'totalInscritos': 5,
};

void main() {
  group('Salida.fromJson', () {
    test('parses required fields', () {
      final s = Salida.fromJson(_baseJson);
      expect(s.id, 'a1b2c3d4-0000-0000-0000-000000000042');
      expect(s.nombre, 'Cerro Colorado');
      expect(s.estado, 'PLANIFICADA');
      expect(s.capacidadMaxima, 20);
      expect(s.totalInscritos, 5);
    });

    test('parses fechaInicio as DateTime', () {
      final s = Salida.fromJson(_baseJson);
      expect(s.fechaInicio?.year, 2025);
      expect(s.fechaInicio?.month, 6);
    });

    test('rutaNombre is null when absent', () {
      final s = Salida.fromJson(_baseJson);
      expect(s.rutaNombre, isNull);
    });

    test('parses rutaNombre directly from json', () {
      final json = {..._baseJson, 'rutaNombre': 'Ruta Normal'};
      final s = Salida.fromJson(json);
      expect(s.rutaNombre, 'Ruta Normal');
    });

    test('parses nivelMinimo from nivelMinimoNombre', () {
      final json = {..._baseJson, 'nivelMinimoNombre': 'Intermedio'};
      final s = Salida.fromJson(json);
      expect(s.nivelMinimo, 'Intermedio');
    });

    test('fechaInicio is null when field is absent', () {
      const json = {'id': '1', 'nombre': 'X', 'estado': 'PLANIFICADA'};
      final s = Salida.fromJson(json);
      expect(s.fechaInicio, isNull);
    });
  });

  group('Salida.estado string values', () {
    test('estado is preserved as-is from json', () {
      final s = Salida.fromJson({'id': '1', 'nombre': 'X', 'estado': 'EN_CURSO'});
      expect(s.estado, 'EN_CURSO');
    });

    test('defaults to empty string when estado is absent', () {
      final s = Salida.fromJson({'id': '1', 'nombre': 'X'});
      expect(s.estado, '');
    });

    test('inscripcionesCerradas defaults to false', () {
      final s = Salida.fromJson(_baseJson);
      expect(s.inscripcionesCerradas, isFalse);
    });

    test('inscripcionesCerradas parsed when present', () {
      final s = Salida.fromJson({..._baseJson, 'inscripcionesCerradas': true});
      expect(s.inscripcionesCerradas, isTrue);
    });
  });
}
