import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/core/widgets/app_input.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  group('AppInput', () {
    testWidgets('renders hint text', (tester) async {
      await tester.pumpWidget(_wrap(const AppInput(hint: 'Buscar...')));
      expect(find.text('Buscar...'), findsOneWidget);
    });

    testWidgets('renders label text', (tester) async {
      await tester.pumpWidget(_wrap(const AppInput(label: 'Correo')));
      expect(find.text('Correo'), findsOneWidget);
    });

    testWidgets('calls onChanged as user types', (tester) async {
      String? captured;
      await tester.pumpWidget(_wrap(AppInput(onChanged: (v) => captured = v)));
      await tester.enterText(find.byType(TextField), 'hola');
      expect(captured, 'hola');
    });

    testWidgets('shows error text', (tester) async {
      await tester.pumpWidget(_wrap(const AppInput(errorText: 'Campo requerido')));
      expect(find.text('Campo requerido'), findsOneWidget);
    });

    testWidgets('renders prefixIcon when provided', (tester) async {
      await tester.pumpWidget(_wrap(const AppInput(prefixIcon: Icons.search)));
      expect(find.byIcon(Icons.search), findsOneWidget);
    });

    testWidgets('obscureText hides input content', (tester) async {
      await tester.pumpWidget(_wrap(const AppInput(obscureText: true)));
      final tf = tester.widget<TextField>(find.byType(TextField));
      expect(tf.obscureText, isTrue);
      expect(tf.enableSuggestions, isFalse);
      expect(tf.autocorrect, isFalse);
    });

    testWidgets('controller syncs text', (tester) async {
      final ctrl = TextEditingController(text: 'initial');
      await tester.pumpWidget(_wrap(AppInput(controller: ctrl)));
      expect(find.text('initial'), findsOneWidget);
    });
  });
}
