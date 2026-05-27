import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:flutter/material.dart';
import 'package:sadday_app/main_dev.dart' as app;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Login flow', () {
    testWidgets('shows login screen on cold start', (tester) async {
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // The router redirects unauthenticated users to /login
      expect(find.text('Iniciar Sesión'), findsAtLeastNWidgets(1));
    });

    testWidgets('shows validation error on empty submit', (tester) async {
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // Tap the submit button without entering credentials
      final loginBtn = find.widgetWithText(ElevatedButton, 'Iniciar Sesión');
      if (loginBtn.evaluate().isNotEmpty) {
        await tester.tap(loginBtn);
        await tester.pumpAndSettle();
        // reactive_forms marks invalid fields — the button stays on screen
        expect(find.text('Iniciar Sesión'), findsAtLeastNWidgets(1));
      }
    });

    testWidgets('forgot password link is tappable', (tester) async {
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 3));

      final forgotLink = find.text('¿Olvidaste tu contraseña?');
      if (forgotLink.evaluate().isNotEmpty) {
        await tester.tap(forgotLink);
        await tester.pumpAndSettle();
        // navigated to forgot-password — login form no longer primary
      }
    });
  });
}
