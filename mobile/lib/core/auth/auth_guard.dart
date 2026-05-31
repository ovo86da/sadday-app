import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'auth_provider.dart';
import 'auth_state.dart';
import 'user_model.dart';

// Redirect global del router: evalúa auth state en cada navegación.
String? authRedirect(BuildContext context, GoRouterState state) {
  final container = ProviderScope.containerOf(context);
  final auth = container.read(authNotifierProvider).asData?.value;
  final location = state.matchedLocation;

  const publicRoutes = [
    '/login', '/forgot-password', '/reset-password', '/registro/completar',
  ];
  final isPublic = publicRoutes.any((r) => location.startsWith(r));

  if (auth is AuthAuthenticated) return isPublic ? '/dashboard' : null;
  if (auth is AuthLocked)        return location == '/unlock' ? null : '/unlock';
  return isPublic ? null : '/login';
}

// Guard de rol — úsalo en redirect de rutas con permisos elevados.
String? roleRedirect(
  BuildContext context,
  GoRouterState state, {
  required List<UserRole> allowedRoles,
}) {
  final container = ProviderScope.containerOf(context);
  final auth = container.read(authNotifierProvider).asData?.value;
  if (auth is! AuthAuthenticated) return '/login';
  if (!allowedRoles.contains(auth.user.rol)) return '/403';
  return null;
}

// Guard para /notificaciones: permite privilegiados y Jefes de Salida activos.
String? notificacionesRedirect(BuildContext context, GoRouterState state) {
  final container = ProviderScope.containerOf(context);
  final auth = container.read(authNotifierProvider).asData?.value;
  if (auth is! AuthAuthenticated) return '/login';
  if (!auth.user.puedeVerNotificaciones) return '/403';
  return null;
}
