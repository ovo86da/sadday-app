import 'package:logger/logger.dart';
import 'app_config.dart';

// Silenciado completamente en prod — no loguear tokens, contraseñas ni PII.
abstract final class AppLogger {
  static final _logger = Logger(
    printer: PrettyPrinter(methodCount: 0, errorMethodCount: 5, colors: false),
  );

  static void d(String message, [Object? error]) {
    if (!AppConfig.isProd) _logger.d(message, error: error);
  }

  static void i(String message, [Object? error]) {
    if (!AppConfig.isProd) _logger.i(message, error: error);
  }

  static void w(String message, [Object? error]) {
    if (!AppConfig.isProd) _logger.w(message, error: error);
  }

  static void e(String message, [Object? error, StackTrace? stack]) {
    if (!AppConfig.isProd) _logger.e(message, error: error, stackTrace: stack);
  }
}
