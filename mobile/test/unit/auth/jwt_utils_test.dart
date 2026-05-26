import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:sadday_app/core/auth/jwt_utils.dart';

String _makeJwt(int expUnixSeconds) {
  final header = base64UrlEncode(utf8.encode('{"alg":"HS256","typ":"JWT"}'));
  final payload =
      base64UrlEncode(utf8.encode('{"sub":"1","exp":$expUnixSeconds}'));
  return '$header.$payload.fakesignature';
}

String _makeJwtNoExp() {
  final header = base64UrlEncode(utf8.encode('{"alg":"HS256","typ":"JWT"}'));
  final payload = base64UrlEncode(utf8.encode('{"sub":"1"}'));
  return '$header.$payload.fakesignature';
}

void main() {
  group('JwtUtils.isExpiredWithBuffer', () {
    test('returns true for a token expired in the past', () {
      final past =
          DateTime.now().subtract(const Duration(hours: 1)).millisecondsSinceEpoch ~/
              1000;
      expect(JwtUtils.isExpiredWithBuffer(_makeJwt(past)), isTrue);
    });

    test('returns true for a token expiring within the buffer window', () {
      final nearFuture =
          DateTime.now().add(const Duration(seconds: 10)).millisecondsSinceEpoch ~/
              1000;
      // Default buffer is 30s — a token expiring in 10s should be considered expired
      expect(JwtUtils.isExpiredWithBuffer(_makeJwt(nearFuture)), isTrue);
    });

    test('returns false for a valid token well outside the buffer', () {
      final future =
          DateTime.now().add(const Duration(hours: 1)).millisecondsSinceEpoch ~/
              1000;
      expect(JwtUtils.isExpiredWithBuffer(_makeJwt(future)), isFalse);
    });

    test('returns false when no exp claim present', () {
      expect(JwtUtils.isExpiredWithBuffer(_makeJwtNoExp()), isFalse);
    });

    test('returns true for a malformed token', () {
      expect(JwtUtils.isExpiredWithBuffer('not.a.token'), isTrue);
    });

    test('returns true for empty string', () {
      expect(JwtUtils.isExpiredWithBuffer(''), isTrue);
    });

    test('custom bufferSeconds respected', () {
      // Token expiring in 60s — should be fine with 30s buffer, expired with 90s buffer
      final in60s =
          DateTime.now().add(const Duration(seconds: 60)).millisecondsSinceEpoch ~/
              1000;
      expect(
          JwtUtils.isExpiredWithBuffer(_makeJwt(in60s), bufferSeconds: 30),
          isFalse);
      expect(
          JwtUtils.isExpiredWithBuffer(_makeJwt(in60s), bufferSeconds: 90),
          isTrue);
    });
  });
}
