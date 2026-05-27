import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../auth/auth_provider.dart';

final inactivityNotifierProvider =
    NotifierProvider<InactivityNotifier, void>(InactivityNotifier.new);

class InactivityNotifier extends Notifier<void> {
  static const _timeout = Duration(minutes: 10);
  Timer? _timer;

  @override
  void build() {
    ref.onDispose(() => _timer?.cancel());
  }

  // Llama en cada gesto del usuario (GestureDetector en la raíz de la app).
  void resetTimer() {
    _timer?.cancel();
    _timer = Timer(_timeout, _onTimeout);
  }

  void _onTimeout() {
    ref.read(authNotifierProvider.notifier).onInactivityTimeout();
  }
}
