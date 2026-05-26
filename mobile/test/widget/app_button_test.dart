import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/core/widgets/app_button.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: Center(child: child)));

void main() {
  group('AppButton', () {
    testWidgets('renders label', (tester) async {
      await tester.pumpWidget(_wrap(AppButton(label: 'Guardar', onPressed: () {})));
      expect(find.text('Guardar'), findsOneWidget);
    });

    testWidgets('calls onPressed when tapped', (tester) async {
      var called = false;
      await tester.pumpWidget(_wrap(AppButton(label: 'OK', onPressed: () => called = true)));
      await tester.tap(find.text('OK'));
      expect(called, isTrue);
    });

    testWidgets('shows CircularProgressIndicator when loading=true', (tester) async {
      await tester.pumpWidget(_wrap(AppButton(label: 'OK', onPressed: () {}, loading: true)));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('OK'), findsNothing);
    });

    testWidgets('is disabled when loading=true', (tester) async {
      var called = false;
      await tester.pumpWidget(_wrap(AppButton(label: 'OK', onPressed: () => called = true, loading: true)));
      await tester.tap(find.byType(ElevatedButton), warnIfMissed: false);
      expect(called, isFalse);
    });

    testWidgets('renders icon when provided', (tester) async {
      await tester.pumpWidget(_wrap(AppButton(label: 'Add', onPressed: () {}, icon: Icons.add)));
      expect(find.byIcon(Icons.add), findsOneWidget);
    });

    testWidgets('secondary variant renders OutlinedButton', (tester) async {
      await tester.pumpWidget(_wrap(AppButton(
        label: 'Cancel',
        onPressed: () {},
        variant: AppButtonVariant.secondary,
      )));
      expect(find.byType(OutlinedButton), findsOneWidget);
    });

    testWidgets('ghost variant renders TextButton', (tester) async {
      await tester.pumpWidget(_wrap(AppButton(
        label: 'Skip',
        onPressed: () {},
        variant: AppButtonVariant.ghost,
      )));
      expect(find.byType(TextButton), findsOneWidget);
    });

    testWidgets('fullWidth expands to SizedBox with infinite width', (tester) async {
      await tester.pumpWidget(_wrap(AppButton(
        label: 'Full',
        onPressed: () {},
        fullWidth: true,
      )));
      final box = tester.widget<SizedBox>(find.byType(SizedBox).first);
      expect(box.width, double.infinity);
    });
  });
}
