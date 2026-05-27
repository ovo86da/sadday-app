import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

// En iOS oculta el contenido sensible cuando la app pasa a background/inactive.
// En Android la protección se hace con FLAG_SECURE en MainActivity.kt.
class AppLifecycleObserver extends WidgetsBindingObserver {
  AppLifecycleObserver() {
    WidgetsBinding.instance.addObserver(this);
  }

  final _obscured = ValueNotifier<bool>(false);
  ValueListenable<bool> get obscured => _obscured;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!Platform.isIOS) return;
    _obscured.value =
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused;
  }

  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _obscured.dispose();
  }
}
