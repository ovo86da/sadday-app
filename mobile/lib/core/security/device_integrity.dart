import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_jailbreak_detection/flutter_jailbreak_detection.dart';

/// Comprobación de integridad del dispositivo (K-01).
///
/// Detecta si el dispositivo está rooteado (Android) o con jailbreak (iOS).
/// El resultado es **informativo**: la app avisa al socio pero no bloquea el
/// acceso. Bloquear castigaría a usuarios legítimos que rootean su propio
/// dispositivo, y el detector es evadible por alguien determinado, así que
/// tratarlo como barrera daría una seguridad que no tiene.
///
/// Se usa solo en el flavor de producción ([main_prod.dart]).
class DeviceIntegrity {
  const DeviceIntegrity._();

  /// `true` solo cuando la detección confirma un dispositivo comprometido.
  ///
  /// Devuelve `false` ante cualquier duda: plataforma no móvil, plugin no
  /// disponible o error del canal nativo. El getter `jailbroken` del paquete
  /// devuelve `true` cuando el canal falla, lo que alarmaría a todo el mundo
  /// si lo propagáramos — de ahí que los errores se traten como "sin detección"
  /// en lugar de "comprometido".
  static Future<bool> isCompromised() async {
    if (kIsWeb || !(Platform.isAndroid || Platform.isIOS)) return false;
    try {
      return await FlutterJailbreakDetection.jailbroken;
    } catch (e, st) {
      debugPrint('DeviceIntegrity: detección no disponible ($e)');
      debugPrintStack(stackTrace: st, maxFrames: 4);
      return false;
    }
  }
}
