import 'dart:convert';

abstract final class JwtUtils {
  // Verifica solo el campo exp — NO valida firma (responsabilidad del servidor).
  // Devuelve true si el token expira dentro de los próximos [bufferSeconds].
  static bool isExpiredWithBuffer(String token, {int bufferSeconds = 30}) {
    try {
      final parts = token.split('.');
      if (parts.length != 3) return true;
      final padded = base64Url.normalize(parts[1]);
      final payload = jsonDecode(utf8.decode(base64Url.decode(padded))) as Map<String, dynamic>;
      final exp = payload['exp'];
      if (exp == null) return false;
      final expiry = DateTime.fromMillisecondsSinceEpoch((exp as int) * 1000);
      return DateTime.now().add(Duration(seconds: bufferSeconds)).isAfter(expiry);
    } catch (_) {
      return true;
    }
  }
}
