// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get appTitle => 'Sadday Club Andino';

  @override
  String get loginTitle => 'Iniciar Sesión';

  @override
  String get loginButton => 'Iniciar Sesión';

  @override
  String get loginUserLabel => 'Usuario';

  @override
  String get loginPasswordLabel => 'Contraseña';

  @override
  String get loginForgotPassword => '¿Olvidaste tu contraseña?';

  @override
  String get mfaTitle => 'Verificación en dos pasos';

  @override
  String get mfaInstruction =>
      'Ingresa el código de 6 dígitos enviado a tu correo';

  @override
  String get errorNetwork =>
      'Sin conexión a internet. Verifica tu red e intenta de nuevo.';

  @override
  String get errorUnauthorized => 'Sesión expirada. Inicia sesión nuevamente.';

  @override
  String get errorForbidden => 'No tienes permisos para realizar esta acción.';

  @override
  String get errorServer => 'Error del servidor. Intenta más tarde.';

  @override
  String get actionSave => 'Guardar';

  @override
  String get actionCancel => 'Cancelar';

  @override
  String get actionDelete => 'Eliminar';

  @override
  String get actionConfirm => 'Confirmar';

  @override
  String get actionShare => 'Compartir';

  @override
  String get actionDownload => 'Descargar';

  @override
  String get actionExport => 'Exportar';

  @override
  String get navDashboard => 'Inicio';

  @override
  String get navSalidas => 'Salidas';

  @override
  String get navInformes => 'Informes';

  @override
  String get navPerfil => 'Mi Perfil';

  @override
  String get navMas => 'Más';

  @override
  String get labelSearch => 'Buscar';

  @override
  String labelPage(int page, int total) {
    return 'Página $page de $total';
  }

  @override
  String get emptyStateTitle => 'Sin resultados';

  @override
  String get emptyStateMessage => 'No se encontraron elementos.';
}
