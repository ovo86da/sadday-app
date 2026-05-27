import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'app.dart';
import 'core/api/cookie_jar_provider.dart';
import 'core/provider_retry.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await dotenv.load(fileName: '.env.dev');

  final appDir = await getApplicationSupportDirectory();
  final cookieJar = PersistCookieJar(
    ignoreExpires: true,
    storage: FileStorage('${appDir.path}/.cookies'),
  );

  runApp(
    ProviderScope(
      retry: noProviderRetry,
      overrides: [cookieJarProvider.overrideWithValue(cookieJar)],
      child: const SaddayApp(),
    ),
  );
}
