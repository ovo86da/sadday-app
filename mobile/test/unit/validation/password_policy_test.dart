import 'package:flutter_test/flutter_test.dart';
import 'package:reactive_forms/reactive_forms.dart';
import 'package:sadday_app/core/validation/password_policy.dart';

void main() {
  group('PasswordPolicy.validate', () {
    test('acepta una contraseña que cumple todas las reglas', () {
      expect(PasswordPolicy.validate('Montania2026!'), isNull);
    });

    test('rechaza vacío', () {
      expect(PasswordPolicy.validate(''), 'Requerido');
      expect(PasswordPolicy.validate(null), 'Requerido');
    });

    test('rechaza menos de 12 caracteres', () {
      // 11 caracteres, cumple el resto de las reglas.
      expect(PasswordPolicy.validate('Montania26!'),
          'Mínimo 12 caracteres');
    });

    test('acepta exactamente 12 caracteres', () {
      expect(PasswordPolicy.validate('Montania26!a'), isNull);
    });

    test('rechaza más de 200 caracteres', () {
      final tooLong = 'Aa1!${'x' * 200}';
      expect(PasswordPolicy.validate(tooLong), 'Máximo 200 caracteres');
    });

    test('rechaza sin mayúscula', () {
      expect(PasswordPolicy.validate('montania2026!'),
          'Debe incluir al menos una mayúscula');
    });

    test('rechaza sin minúscula', () {
      expect(PasswordPolicy.validate('MONTANIA2026!'),
          'Debe incluir al menos una minúscula');
    });

    test('rechaza sin número', () {
      expect(PasswordPolicy.validate('MontaniaSadday!'),
          'Debe incluir al menos un número');
    });

    test('rechaza sin símbolo', () {
      expect(PasswordPolicy.validate('Montania20261'),
          'Debe incluir al menos un símbolo');
    });

    test('el espacio no cuenta como símbolo', () {
      expect(PasswordPolicy.validate('Montania 2026'),
          'Debe incluir al menos un símbolo');
    });

    test('acepta distintos símbolos imprimibles', () {
      for (final symbol in ['!', '@', '#', r'$', '%', '-', '_', '.', '?']) {
        expect(PasswordPolicy.validate('Montania2026$symbol'), isNull,
            reason: 'el símbolo $symbol debería aceptarse');
      }
    });

    test('la longitud se valida antes que la complejidad', () {
      // Corta y además sin símbolo: debe quejarse primero de la longitud.
      expect(PasswordPolicy.validate('Abc123'), 'Mínimo 12 caracteres');
    });
  });

  group('PasswordPolicy.reactiveValidator', () {
    test('no reporta error cuando la contraseña cumple', () {
      final control = FormControl<String>(
        value: 'Montania2026!',
        validators: [PasswordPolicy.reactiveValidator],
      );
      expect(control.valid, isTrue);
      expect(control.errors, isEmpty);
    });

    test('reporta el mensaje bajo errorKey cuando falla', () {
      final control = FormControl<String>(
        value: 'montania2026!',
        validators: [PasswordPolicy.reactiveValidator],
      );
      expect(control.valid, isFalse);
      expect(control.errors[PasswordPolicy.errorKey],
          'Debe incluir al menos una mayúscula');
    });

    test('un control vacío es inválido', () {
      final control = FormControl<String>(
        validators: [PasswordPolicy.reactiveValidator],
      );
      expect(control.valid, isFalse);
      expect(control.errors[PasswordPolicy.errorKey], 'Requerido');
    });
  });

  group('PasswordPolicy.hint', () {
    test('nombra la longitud mínima real exigida por el backend', () {
      expect(PasswordPolicy.minLength, 12);
      expect(PasswordPolicy.hint, contains('12'));
      expect(PasswordPolicy.hint, isNot(contains('8')));
    });
  });
}
