import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Almacén seguro para datos sensibles del ciclo de sesión.
///
/// iOS  → Keychain con [KeychainAccessibility.unlocked]: solo accesible
///         con la pantalla desbloqueada (no en background ni bloqueado).
/// Android → EncryptedSharedPreferences respaldado por Android Keystore.
class SecureStorageService {
  const SecureStorageService._();
  static const SecureStorageService instance = SecureStorageService._();

  // iOS: Keychain accesible solo con pantalla desbloqueada (Secure Enclave).
  // Android: cifrado automático por flutter_secure_storage v10+ (Jetpack Security
  // fue deprecado por Google; la librería usa sus propios cifrados desde v10).
  static final _storage = const FlutterSecureStorage(
    iOptions: IOSOptions(accessibility: KeychainAccessibility.unlocked),
  );

  static const _keyRefreshToken    = 'refresh_token';
  static const _keyBiometricEnabled = 'biometric_enabled';

  Future<void>    saveRefreshToken(String token) => _storage.write(key: _keyRefreshToken, value: token);
  Future<String?> getRefreshToken()              => _storage.read(key: _keyRefreshToken);
  Future<void>    deleteRefreshToken()           => _storage.delete(key: _keyRefreshToken);

  Future<void> setBiometricEnabled(bool enabled) =>
      _storage.write(key: _keyBiometricEnabled, value: enabled.toString());

  Future<bool> isBiometricEnabled() async =>
      await _storage.read(key: _keyBiometricEnabled) == 'true';

  Future<void> clearAll() => _storage.deleteAll();
}
