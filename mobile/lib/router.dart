import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'core/auth/auth_guard.dart';
import 'core/auth/auth_provider.dart';
import 'core/auth/auth_state.dart';
import 'core/auth/user_model.dart';
import 'core/theme/app_colors.dart';
import 'core/widgets/app_nav_shell.dart';
import 'features/actas/presentation/screens/acta_detail_screen.dart';
import 'features/actas/presentation/screens/actas_screen.dart';
import 'features/admin/presentation/screens/admin_screen.dart';
import 'features/auth/presentation/screens/complete_registration_screen.dart';
import 'features/auth/presentation/screens/country_challenge_screen.dart';
import 'features/auth/presentation/screens/forgot_password_screen.dart';
import 'features/auth/presentation/screens/login_screen.dart';
import 'features/auth/presentation/screens/mfa_screen.dart';
import 'features/auth/presentation/screens/reset_password_screen.dart';
import 'features/auth/presentation/screens/unlock_screen.dart';
import 'features/contactos/presentation/screens/contactos_screen.dart';
import 'features/dashboard/presentation/screens/dashboard_screen.dart';
import 'features/estadisticas/presentation/screens/estadisticas_screen.dart';
import 'features/informes/presentation/screens/informe_detail_screen.dart';
import 'features/informes/presentation/screens/informes_screen.dart';
import 'features/montanas/presentation/screens/montana_detail_screen.dart';
import 'features/montanas/presentation/screens/montanas_screen.dart';
import 'features/notificaciones/presentation/screens/notificaciones_screen.dart';
import 'features/planificador/presentation/screens/planificador_screen.dart';
import 'features/perfil/presentation/screens/perfil_screen.dart';
import 'features/rutas/presentation/screens/ruta_detail_screen.dart';
import 'features/rutas/presentation/screens/rutas_screen.dart';
import 'features/salidas/presentation/screens/salida_detail_screen.dart';
import 'features/salidas/presentation/screens/salidas_screen.dart';
import 'features/socios/presentation/screens/socio_detail_screen.dart';
import 'features/socios/presentation/screens/socios_screen.dart';
import 'features/reglamento/presentation/screens/reglamento_screen.dart';
import 'features/teoria/presentation/screens/teoria_screen.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>();
final _shellNavigatorKey = GlobalKey<NavigatorState>();

// Listenable que GoRouter usa para re-evaluar el redirect cuando cambia la auth.
class _AuthRefreshListenable extends ChangeNotifier {
  _AuthRefreshListenable(Ref ref) {
    ref.listen<AsyncValue<AuthState>>(authNotifierProvider, (_, _) {
      notifyListeners();
    });
  }
}

final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _AuthRefreshListenable(ref);
  ref.onDispose(refresh.dispose);

  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/startup',
    redirect: authRedirect,
    refreshListenable: refresh,
    routes: _routes,
  );
});

final List<RouteBase> _routes = [
    // ── Startup — visible solo mientras authNotifierProvider está cargando ──
    GoRoute(
      path: '/startup',
      builder: (_, _) => const _StartupScreen(),
    ),

    // ── Rutas públicas (fuera del shell) ────────────────────────────────────
    GoRoute(
      path: '/login',
      builder: (context, _) => const LoginScreen(),
    ),
    GoRoute(
      path: '/mfa',
      builder: (_, s) => MfaScreen(challengeToken: s.extra as String),
    ),
    GoRoute(
      path: '/country-challenge',
      builder: (_, s) => CountryChallengeScreen(token: s.extra as String),
    ),
    GoRoute(
      path: '/forgot-password',
      builder: (context, _) => const ForgotPasswordScreen(),
    ),
    GoRoute(
      path: '/reset-password',
      builder: (_, s) =>
          ResetPasswordScreen(token: s.uri.queryParameters['token'] ?? ''),
    ),
    GoRoute(
      path: '/registro/completar',
      builder: (_, s) => CompleteRegistrationScreen(
        invitationToken: s.uri.queryParameters['token'] ?? '',
      ),
    ),
    GoRoute(
      path: '/unlock',
      builder: (context, _) => const UnlockScreen(),
    ),
    GoRoute(
      path: '/403',
      builder: (context, _) => const _PermissionDenied(),
    ),

    // ── Rutas autenticadas — ShellRoute con Bottom Nav ─────────────────────
    ShellRoute(
      navigatorKey: _shellNavigatorKey,
      builder: (context, state, child) => AppNavShell(child: child),
      routes: [
        // Dashboard
        GoRoute(
          path: '/dashboard',
          builder: (context, _) => const DashboardScreen(),
        ),

        // Salidas
        GoRoute(
          path: '/salidas',
          builder: (context, _) => const SalidasScreen(),
          routes: [
            GoRoute(
              path: ':id',
              builder: (_, s) =>
                  SalidaDetailScreen(id: s.pathParameters['id']!),
            ),
          ],
        ),

        // Informes
        GoRoute(
          path: '/informes',
          builder: (context, _) => const InformesScreen(),
          routes: [
            GoRoute(
              path: ':salidaId',
              builder: (_, s) => InformeDetailScreen(
                  salidaId: s.pathParameters['salidaId']!),
            ),
          ],
        ),

        // Mi Perfil
        GoRoute(
          path: '/perfil',
          builder: (context, _) => const PerfilScreen(),
        ),

        // Montañas (Drawer)
        GoRoute(
          path: '/montanas',
          builder: (context, _) => const MontanasScreen(),
          routes: [
            GoRoute(
              path: ':id',
              builder: (_, s) =>
                  MontanaDetailScreen(id: int.parse(s.pathParameters['id']!)),
            ),
          ],
        ),

        // Rutas (Drawer)
        GoRoute(
          path: '/rutas',
          builder: (context, _) => const RutasScreen(),
          routes: [
            GoRoute(
              path: ':id',
              builder: (_, s) =>
                  RutaDetailScreen(id: int.parse(s.pathParameters['id']!)),
            ),
          ],
        ),

        // Actas (Drawer)
        GoRoute(
          path: '/actas',
          builder: (context, _) => const ActasScreen(),
          routes: [
            GoRoute(
              path: ':id',
              builder: (_, s) =>
                  ActaDetailScreen(id: s.pathParameters['id']!),
            ),
          ],
        ),

        // Estadísticas (Drawer)
        GoRoute(
          path: '/estadisticas',
          builder: (context, _) => const EstadisticasScreen(),
        ),

        // Planificador (Drawer)
        GoRoute(
          path: '/planificador',
          builder: (context, s) => PlanificadorScreen(
            initialRutaId:
                int.tryParse(s.uri.queryParameters['rutaId'] ?? ''),
          ),
        ),

        // Notificaciones (Drawer — privilegiados + Jefe de Salida activo)
        GoRoute(
          path: '/notificaciones',
          redirect: notificacionesRedirect,
          builder: (context, _) => const NotificacionesScreen(),
        ),

        // Socios (Drawer — ADMIN / SECRETARIA / DIRECTIVO)
        GoRoute(
          path: '/socios',
          builder: (context, _) => const SociosScreen(),
          routes: [
            GoRoute(
              path: ':id',
              builder: (_, s) =>
                  SocioDetailScreen(id: s.pathParameters['id']!),
            ),
          ],
        ),

        // Teoría (Drawer — todos los roles)
        GoRoute(
          path: '/teoria',
          builder: (context, _) => const TeoriaScreen(),
        ),
        // Reglamento (Drawer — todos los roles)
        GoRoute(
          path: '/reglamento',
          builder: (context, _) => const ReglamentoScreen(),
        ),

        // Contactos (Drawer — ADMIN / SECRETARIA)
        GoRoute(
          path: '/contactos',
          builder: (context, _) => const ContactosScreen(),
        ),

        // Administración (Drawer — solo ADMIN / SECRETARIA)
        GoRoute(
          path: '/admin',
          redirect: (ctx, st) => roleRedirect(ctx, st,
              allowedRoles: const [UserRole.admin, UserRole.secretaria]),
          builder: (context, _) => const AdminScreen(),
        ),
      ],
    ),
];

class _StartupScreen extends StatelessWidget {
  const _StartupScreen();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: AppColors.background,
      body: Center(child: CircularProgressIndicator()),
    );
  }
}

class _PermissionDenied extends StatelessWidget {
  const _PermissionDenied();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Sin permisos')),
      body: const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.lock_outline, color: AppColors.mutedFg, size: 56),
            SizedBox(height: 16),
            Text('No tienes permisos para acceder a esta sección.',
                style: TextStyle(color: AppColors.mutedFg),
                textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}
