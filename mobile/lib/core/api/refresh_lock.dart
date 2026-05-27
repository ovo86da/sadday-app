import 'package:synchronized/synchronized.dart';

// Mutex para evitar race condition en refresh de token (MASVS-AUTH / MITRE T1557).
// El backend rota el refresh token — dos refreshes paralelos revocan la sesión.
final _lock = Lock();

Future<T> withRefreshLock<T>(Future<T> Function() action) =>
    _lock.synchronized(action);
