import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SecureStorageService {
  const SecureStorageService._();
  static const SecureStorageService instance = SecureStorageService._();

  static final _storage = const FlutterSecureStorage(
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
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
