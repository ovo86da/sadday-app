import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'app.dart';
import 'core/provider_retry.dart';
import 'core/security/device_integrity.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await dotenv.load(fileName: '.env.prod');
  // K-01: aviso informativo si el dispositivo tiene root/jailbreak. No bloquea
  // el acceso; solo se comprueba en producción.
  final deviceCompromised = await DeviceIntegrity.isCompromised();
  runApp(ProviderScope(
    retry: noProviderRetry,
    child: SaddayApp(deviceCompromised: deviceCompromised),
  ));
}
