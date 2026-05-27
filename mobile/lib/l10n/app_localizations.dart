import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_es.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('es'),
  ];

  /// Application title
  ///
  /// In es, this message translates to:
  /// **'Sadday Club Andino'**
  String get appTitle;

  /// Login screen title
  ///
  /// In es, this message translates to:
  /// **'Iniciar Sesión'**
  String get loginTitle;

  /// Login submit button label
  ///
  /// In es, this message translates to:
  /// **'Iniciar Sesión'**
  String get loginButton;

  /// Username field label on login
  ///
  /// In es, this message translates to:
  /// **'Usuario'**
  String get loginUserLabel;

  /// Password field label on login
  ///
  /// In es, this message translates to:
  /// **'Contraseña'**
  String get loginPasswordLabel;

  /// Forgot password link
  ///
  /// In es, this message translates to:
  /// **'¿Olvidaste tu contraseña?'**
  String get loginForgotPassword;

  /// MFA screen title
  ///
  /// In es, this message translates to:
  /// **'Verificación en dos pasos'**
  String get mfaTitle;

  /// MFA instruction text
  ///
  /// In es, this message translates to:
  /// **'Ingresa el código de 6 dígitos enviado a tu correo'**
  String get mfaInstruction;

  /// No internet connection error
  ///
  /// In es, this message translates to:
  /// **'Sin conexión a internet. Verifica tu red e intenta de nuevo.'**
  String get errorNetwork;

  /// Session expired error
  ///
  /// In es, this message translates to:
  /// **'Sesión expirada. Inicia sesión nuevamente.'**
  String get errorUnauthorized;

  /// Permission denied error
  ///
  /// In es, this message translates to:
  /// **'No tienes permisos para realizar esta acción.'**
  String get errorForbidden;

  /// Server error message
  ///
  /// In es, this message translates to:
  /// **'Error del servidor. Intenta más tarde.'**
  String get errorServer;

  /// Save action label
  ///
  /// In es, this message translates to:
  /// **'Guardar'**
  String get actionSave;

  /// Cancel action label
  ///
  /// In es, this message translates to:
  /// **'Cancelar'**
  String get actionCancel;

  /// Delete action label
  ///
  /// In es, this message translates to:
  /// **'Eliminar'**
  String get actionDelete;

  /// Confirm action label
  ///
  /// In es, this message translates to:
  /// **'Confirmar'**
  String get actionConfirm;

  /// Share action label
  ///
  /// In es, this message translates to:
  /// **'Compartir'**
  String get actionShare;

  /// Download action label
  ///
  /// In es, this message translates to:
  /// **'Descargar'**
  String get actionDownload;

  /// Export action label
  ///
  /// In es, this message translates to:
  /// **'Exportar'**
  String get actionExport;

  /// Dashboard nav label
  ///
  /// In es, this message translates to:
  /// **'Inicio'**
  String get navDashboard;

  /// Salidas nav label
  ///
  /// In es, this message translates to:
  /// **'Salidas'**
  String get navSalidas;

  /// Informes nav label
  ///
  /// In es, this message translates to:
  /// **'Informes'**
  String get navInformes;

  /// Profile nav label
  ///
  /// In es, this message translates to:
  /// **'Mi Perfil'**
  String get navPerfil;

  /// More nav label
  ///
  /// In es, this message translates to:
  /// **'Más'**
  String get navMas;

  /// Search placeholder
  ///
  /// In es, this message translates to:
  /// **'Buscar'**
  String get labelSearch;

  /// Pagination label
  ///
  /// In es, this message translates to:
  /// **'Página {page} de {total}'**
  String labelPage(int page, int total);

  /// Empty state title
  ///
  /// In es, this message translates to:
  /// **'Sin resultados'**
  String get emptyStateTitle;

  /// Empty state message
  ///
  /// In es, this message translates to:
  /// **'No se encontraron elementos.'**
  String get emptyStateMessage;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'es'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
