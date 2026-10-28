import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/features/notificaciones/domain/models/notificacion_models.dart';

/// Lo que el móvil entiende de la evaluación de nivel (FR-028).
///
/// El caso que de verdad importa es el último: si el servidor añade un tipo de
/// motivo que esta versión de la app no conoce, el fallo seguro es **retener**,
/// no dejar pasar. Una app vieja en el teléfono de quien aprueba no puede
/// convertir un motivo desconocido en un visto bueno.
void main() {
  group('EvaluacionNivel.fromJson', () {
    test('lee los hallazgos con sus dos valores', () {
      final e = EvaluacionNivel.fromJson({
        'apto': false,
        'hallazgos': [
          {
            'escala': 'Hielo',
            'exigeRuta': 'WI3',
            'permiteNivel': 'WI2',
            'tipo': 'EXCEDE',
            'detalle': 'La ruta pide WI3 y el nivel del socio llega a WI2.',
          },
        ],
      });

      expect(e.apto, isFalse);
      expect(e.hallazgos, hasLength(1));
      expect(e.queExceden, hasLength(1));
      expect(e.sinComprobar, isEmpty);
      expect(e.queExceden.first.escala, 'Hielo');
      expect(e.queExceden.first.exigeRuta, 'WI3');
      expect(e.queExceden.first.permiteNivel, 'WI2');
    });

    test('separa lo que excede de lo que no se pudo comprobar', () {
      final e = EvaluacionNivel.fromJson({
        'apto': false,
        'hallazgos': [
          {'tipo': 'EXCEDE', 'detalle': 'a', 'escala': 'Roca'},
          {'tipo': 'SIN_COMPROBAR', 'detalle': 'b', 'escala': 'Senderismo'},
        ],
      });

      expect(e.queExceden.map((h) => h.escala), ['Roca']);
      expect(e.sinComprobar.map((h) => h.escala), ['Senderismo']);
    });

    test('un techo sin fijar llega sin valor, no como cero', () {
      final e = EvaluacionNivel.fromJson({
        'apto': false,
        'hallazgos': [
          {
            'escala': 'Senderismo',
            'exigeRuta': 'Moderado',
            'permiteNivel': null,
            'tipo': 'SIN_COMPROBAR',
            'detalle': 'El nivel no tiene fijado un máximo de senderismo.',
          },
        ],
      });

      expect(e.sinComprobar.first.permiteNivel, isNull);
      expect(e.sinComprobar.first.exigeRuta, 'Moderado');
    });

    test('una evaluación sin hallazgos y apta no inventa motivos', () {
      final e = EvaluacionNivel.fromJson({'apto': true, 'hallazgos': []});

      expect(e.apto, isTrue);
      expect(e.hallazgos, isEmpty);
    });

    test('un json incompleto no se da por bueno', () {
      final e = EvaluacionNivel.fromJson({});

      expect(e.apto, isFalse, reason: 'sin dato, apto es false');
      expect(e.hallazgos, isEmpty);
    });

    test('un tipo desconocido cuenta como «no se pudo comprobar», no como ok',
        () {
      // Si el servidor añade un tipo nuevo y esta app es vieja, retener es el
      // fallo seguro: tratarlo como EXCEDE mentiría sobre lo que se sabe, y
      // descartarlo dejaría pasar a alguien sin que nadie lo mire.
      final e = EvaluacionNivel.fromJson({
        'apto': false,
        'hallazgos': [
          {'tipo': 'ALGO_NUEVO', 'detalle': 'motivo que esta app no conoce'},
        ],
      });

      expect(e.hallazgos, hasLength(1), reason: 'no se descarta en silencio');
      expect(e.sinComprobar, hasLength(1));
      expect(e.queExceden, isEmpty);
    });
  });

  group('AprobacionPendiente.fromJson', () {
    test('trae la evaluación cuando el servidor la manda', () {
      final a = AprobacionPendiente.fromJson({
        'participanteId': 7,
        'salidaId': 'aaaa',
        'salidaNombre': 'Cayambe',
        'socioId': 'bbbb',
        'socioNombre': 'Ana',
        'socioApellido': 'Pérez',
        'evaluacionNivel': {
          'apto': false,
          'hallazgos': [
            {'tipo': 'EXCEDE', 'detalle': 'x', 'escala': 'Hielo'},
          ],
        },
      });

      expect(a.evaluacionNivel, isNotNull);
      expect(a.evaluacionNivel!.queExceden.first.escala, 'Hielo');
    });

    test('sigue funcionando si el servidor no la manda', () {
      final a = AprobacionPendiente.fromJson({
        'participanteId': 7,
        'salidaId': 'aaaa',
        'salidaNombre': 'Cayambe',
        'socioId': 'bbbb',
        'socioNombre': 'Ana',
        'socioApellido': 'Pérez',
      });

      expect(a.evaluacionNivel, isNull);
      expect(a.socioNombre, 'Ana Pérez');
    });
  });
}
