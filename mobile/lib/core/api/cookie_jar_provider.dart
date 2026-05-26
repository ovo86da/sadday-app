import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

// CookieJar compartido entre authDio y dioClient para que la cookie
// HttpOnly del refresh token sea visible en ambos.
final cookieJarProvider = Provider<CookieJar>((ref) => CookieJar());
