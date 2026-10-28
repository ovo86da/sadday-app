import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/features/notificaciones/domain/models/notificacion_models.dart';
import 'package:sadday_app/features/notificaciones/presentation/widgets/motivos_nivel.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

/// Lo que ve en el teléfono quien aprueba una inscripción retenida (FR-028).
///
/// El riesgo no es de maquetación: es que «no alcanza» y «no lo sé» se lean
/// como la misma cosa y alguien firme un hueco de datos creyendo que valoró un
/// riesgo medido.
void main() {
  const excede = EvaluacionNivel(
    apto: false,
    hallazgos: [
      HallazgoNivel(
        tipo: 'EXCEDE',
        detalle: 'La ruta pide WI3 y el nivel del socio llega a WI2.',
        escala: 'Hielo',
        exigeRuta: 'WI3',
        permiteNivel: 'WI2',
      ),
    ],
  );

  const hueco = EvaluacionNivel(
    apto: false,
    hallazgos: [
      HallazgoNivel(
        tipo: 'SIN_COMPROBAR',
        detalle: 'El nivel no tiene fijado un máximo de senderismo.',
        escala: 'Senderismo',
        exigeRuta: 'Moderado',
      ),
    ],
  );

  group('MotivosNivel', () {
    testWidgets('nombra la escala y los dos valores', (tester) async {
      await tester.pumpWidget(_wrap(const MotivosNivel(evaluacion: excede)));

      expect(find.textContaining('Hielo'), findsOneWidget);
      expect(find.textContaining('WI3'), findsOneWidget);
      expect(find.textContaining('WI2'), findsOneWidget);
    });

    testWidgets('lo que excede y lo que falta no se mezclan', (tester) async {
      await tester.pumpWidget(_wrap(const MotivosNivel(evaluacion: excede)));
      expect(find.textContaining('excede el nivel'), findsOneWidget);
      expect(find.textContaining('no se pudo comprobar'), findsNothing);

      await tester.pumpWidget(_wrap(const MotivosNivel(evaluacion: hueco)));
      expect(find.textContaining('no se pudo comprobar'), findsOneWidget);
      expect(find.textContaining('excede el nivel'), findsNothing);
    });

    testWidgets('un hueco avisa de que frena igual', (tester) async {
      await tester.pumpWidget(_wrap(const MotivosNivel(evaluacion: hueco)));

      expect(find.textContaining('no es un visto bueno'), findsOneWidget);
    });

    testWidgets('con los dos tipos aparecen los dos bloques', (tester) async {
      await tester.pumpWidget(_wrap(MotivosNivel(
        evaluacion: EvaluacionNivel(
          apto: false,
          hallazgos: [...excede.hallazgos, ...hueco.hallazgos],
        ),
      )));

      expect(find.textContaining('excede el nivel'), findsOneWidget);
      expect(find.textContaining('no se pudo comprobar'), findsOneWidget);
    });

    testWidgets('sin motivos no ocupa sitio', (tester) async {
      await tester.pumpWidget(_wrap(
        const MotivosNivel(evaluacion: EvaluacionNivel(apto: true, hallazgos: [])),
      ));
      expect(find.byType(Container), findsNothing);

      await tester.pumpWidget(_wrap(const MotivosNivel(evaluacion: null)));
      expect(find.byType(Container), findsNothing);
    });
  });
}
