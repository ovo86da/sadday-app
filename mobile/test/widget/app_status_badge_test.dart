import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/core/widgets/app_status_badge.dart';
import 'package:sadday_app/core/theme/app_colors.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  group('SalidaStatusX.fromString', () {
    test('maps PLANIFICADA', () {
      expect(SalidaStatusX.fromString('PLANIFICADA'), SalidaStatus.planificada);
    });

    test('maps EN_CURSO (underscore normalised)', () {
      expect(SalidaStatusX.fromString('EN_CURSO'), SalidaStatus.enCurso);
    });

    test('maps REALIZADA', () {
      expect(SalidaStatusX.fromString('REALIZADA'), SalidaStatus.realizada);
    });

    test('maps CANCELADA', () {
      expect(SalidaStatusX.fromString('CANCELADA'), SalidaStatus.cancelada);
    });

    test('unknown value falls back to planificada', () {
      expect(SalidaStatusX.fromString('UNKNOWN'), SalidaStatus.planificada);
    });
  });

  group('SalidaStatus labels', () {
    test('planificada label', () => expect(SalidaStatus.planificada.label, 'PLANIFICADA'));
    test('enCurso label', () => expect(SalidaStatus.enCurso.label, 'EN CURSO'));
    test('realizada label', () => expect(SalidaStatus.realizada.label, 'REALIZADA'));
    test('cancelada label', () => expect(SalidaStatus.cancelada.label, 'CANCELADA'));
  });

  group('SalidaStatus colors', () {
    test('planificada color', () => expect(SalidaStatus.planificada.color, AppColors.salidaPlanificada));
    test('realizada color', () => expect(SalidaStatus.realizada.color, AppColors.salidaRealizada));
  });

  group('AppStatusBadge widget', () {
    testWidgets('renders PLANIFICADA label', (tester) async {
      await tester.pumpWidget(_wrap(const AppStatusBadge(status: SalidaStatus.planificada)));
      expect(find.text('PLANIFICADA'), findsOneWidget);
    });

    testWidgets('renders EN CURSO label', (tester) async {
      await tester.pumpWidget(_wrap(const AppStatusBadge(status: SalidaStatus.enCurso)));
      expect(find.text('EN CURSO'), findsOneWidget);
    });

    testWidgets('renders REALIZADA label', (tester) async {
      await tester.pumpWidget(_wrap(const AppStatusBadge(status: SalidaStatus.realizada)));
      expect(find.text('REALIZADA'), findsOneWidget);
    });

    testWidgets('renders CANCELADA label', (tester) async {
      await tester.pumpWidget(_wrap(const AppStatusBadge(status: SalidaStatus.cancelada)));
      expect(find.text('CANCELADA'), findsOneWidget);
    });
  });
}
