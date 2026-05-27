import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/core/widgets/app_badge.dart';
import 'package:sadday_app/core/theme/app_colors.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  group('AppBadge', () {
    testWidgets('renders label text', (tester) async {
      await tester.pumpWidget(_wrap(const AppBadge(label: 'ADMIN')));
      expect(find.text('ADMIN'), findsOneWidget);
    });

    testWidgets('uses secondary color by default', (tester) async {
      await tester.pumpWidget(_wrap(const AppBadge(label: 'ROL')));
      final container = tester.widget<Container>(find.byType(Container).first);
      final decoration = container.decoration as BoxDecoration;
      expect(decoration.color, AppColors.secondary);
    });

    testWidgets('uses provided color', (tester) async {
      await tester.pumpWidget(_wrap(const AppBadge(label: 'ROJO', color: Colors.red)));
      final container = tester.widget<Container>(find.byType(Container).first);
      final decoration = container.decoration as BoxDecoration;
      expect(decoration.color, Colors.red);
    });

    testWidgets('uses provided textColor', (tester) async {
      await tester.pumpWidget(_wrap(const AppBadge(label: 'X', textColor: Colors.black)));
      final text = tester.widget<Text>(find.text('X'));
      expect(text.style?.color, Colors.black);
    });
  });
}
