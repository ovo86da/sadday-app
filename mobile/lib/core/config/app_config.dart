import 'package:flutter_dotenv/flutter_dotenv.dart';

enum AppEnvironment { dev, staging, prod }

abstract final class AppConfig {
  static AppEnvironment get env => switch (dotenv.env['ENV']) {
    'staging' => AppEnvironment.staging,
    'prod'    => AppEnvironment.prod,
    _         => AppEnvironment.dev,
  };

  static String get apiBaseUrl =>
      dotenv.env['API_BASE_URL'] ?? 'https://api-dev.el-sadday.com/api';

  static bool get isProd => env == AppEnvironment.prod;
  static bool get isDev  => env == AppEnvironment.dev;
}
