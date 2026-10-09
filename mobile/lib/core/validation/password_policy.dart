import 'package:reactive_forms/reactive_forms.dart';

/// Política de contraseñas — espejo de la validación del backend.
///
/// El backend aplica `@Size(min = 12, max = 200)` más `@StrongPassword`
/// (`StrongPasswordValidator`: al menos una minúscula, una mayúscula, un dígito
/// y un símbolo). El frontend web replica las mismas reglas en sus schemas zod.
/// Los mensajes de aquí son idénticos a los del web para que el socio vea el
/// mismo texto en ambas plataformas.
///
/// Nota: las clases de carácter usan rangos ASCII, igual que el web. El backend
/// usa `Character.isUpperCase`, que sí reconoce mayúsculas acentuadas (Ñ, É).
/// Una contraseña cuya única mayúscula sea acentuada se rechaza en cliente
/// aunque el backend la aceptaría — es un rechazo conservador, nunca una
/// aceptación indebida, y mantiene la paridad con el web.
class PasswordPolicy {
  const PasswordPolicy._();

  static const int minLength = 12;
  static const int maxLength = 200;

  /// Texto de ayuda para mostrar bajo los campos de contraseña.
  static const String hint =
      'Mínimo $minLength caracteres, con mayúscula, minúscula, número y símbolo.';

  static final RegExp _upper = RegExp(r'[A-Z]');
  static final RegExp _lower = RegExp(r'[a-z]');
  static final RegExp _digit = RegExp(r'[0-9]');
  static final RegExp _symbol = RegExp(r'[^a-zA-Z0-9\s]');

  /// Clave de error que [reactiveValidator] reporta en el `FormControl`.
  static const String errorKey = 'passwordPolicy';

  /// Devuelve el primer mensaje de error, o `null` si la contraseña cumple.
  static String? validate(String? value) {
    final password = value ?? '';
    if (password.isEmpty) return 'Requerido';
    if (password.length < minLength) return 'Mínimo $minLength caracteres';
    if (password.length > maxLength) return 'Máximo $maxLength caracteres';
    if (!_upper.hasMatch(password)) return 'Debe incluir al menos una mayúscula';
    if (!_lower.hasMatch(password)) return 'Debe incluir al menos una minúscula';
    if (!_digit.hasMatch(password)) return 'Debe incluir al menos un número';
    if (!_symbol.hasMatch(password)) return 'Debe incluir al menos un símbolo';
    return null;
  }

  /// Validador para `reactive_forms`. Expone el mensaje de [validate] bajo
  /// [errorKey], de modo que la pantalla lo pueda renderizar tal cual.
  static Validator<dynamic> get reactiveValidator =>
      Validators.delegate((control) {
        final error = validate(control.value as String?);
        return error == null ? null : {errorKey: error};
      });
}
