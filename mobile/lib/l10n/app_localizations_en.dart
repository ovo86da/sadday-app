// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'Sadday Andean Club';

  @override
  String get loginTitle => 'Sign In';

  @override
  String get loginButton => 'Sign In';

  @override
  String get loginUserLabel => 'Username';

  @override
  String get loginPasswordLabel => 'Password';

  @override
  String get loginForgotPassword => 'Forgot your password?';

  @override
  String get mfaTitle => 'Two-factor verification';

  @override
  String get mfaInstruction => 'Enter the 6-digit code sent to your email';

  @override
  String get errorNetwork =>
      'No internet connection. Check your network and try again.';

  @override
  String get errorUnauthorized => 'Session expired. Please sign in again.';

  @override
  String get errorForbidden =>
      'You don\'t have permission to perform this action.';

  @override
  String get errorServer => 'Server error. Please try again later.';

  @override
  String get actionSave => 'Save';

  @override
  String get actionCancel => 'Cancel';

  @override
  String get actionDelete => 'Delete';

  @override
  String get actionConfirm => 'Confirm';

  @override
  String get actionShare => 'Share';

  @override
  String get actionDownload => 'Download';

  @override
  String get actionExport => 'Export';

  @override
  String get navDashboard => 'Home';

  @override
  String get navSalidas => 'Trips';

  @override
  String get navInformes => 'Reports';

  @override
  String get navPerfil => 'My Profile';

  @override
  String get navMas => 'More';

  @override
  String get labelSearch => 'Search';

  @override
  String labelPage(int page, int total) {
    return 'Page $page of $total';
  }

  @override
  String get emptyStateTitle => 'No results';

  @override
  String get emptyStateMessage => 'No items found.';
}
