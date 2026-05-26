import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../auth/auth_provider.dart';
import '../auth/auth_state.dart';
import '../auth/user_model.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import 'informe_pendiente_guard.dart';

class AppNavShell extends ConsumerStatefulWidget {
  const AppNavShell({required this.child, super.key});
  final Widget child;

  @override
  ConsumerState<AppNavShell> createState() => _AppNavShellState();
}

class _AppNavShellState extends ConsumerState<AppNavShell> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();

  static const _tabRoutes = ['/dashboard', '/salidas', '/informes', '/perfil'];

  int _selectedIndex(String location) {
    for (var i = 0; i < _tabRoutes.length; i++) {
      if (location.startsWith(_tabRoutes[i])) return i;
    }
    return 4;
  }

  void _onTap(int index) {
    if (index == 4) {
      _scaffoldKey.currentState?.openDrawer();
      return;
    }
    context.go(_tabRoutes[index]);
  }

  @override
  Widget build(BuildContext context) {
    final location = GoRouterState.of(context).uri.path;
    final idx = _selectedIndex(location);
    final authVal = ref.watch(authNotifierProvider).asData?.value;
    final user = authVal is AuthAuthenticated ? authVal.user : null;
    final role = user?.rol;

    return Scaffold(
      key: _scaffoldKey,
      drawer: _AppDrawer(currentLocation: location, user: user, role: role),
      body: InformePendienteGuard(child: widget.child),
      bottomNavigationBar: NavigationBar(
        selectedIndex: idx > 3 ? 4 : idx,
        onDestinationSelected: _onTap,
        backgroundColor: AppColors.sidebar,
        indicatorColor: AppColors.primary.withValues(alpha: 0.18),
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            selectedIcon: Icon(Icons.dashboard),
            label: 'Inicio',
          ),
          NavigationDestination(
            icon: Icon(Icons.hiking_outlined),
            selectedIcon: Icon(Icons.hiking),
            label: 'Salidas',
          ),
          NavigationDestination(
            icon: Icon(Icons.description_outlined),
            selectedIcon: Icon(Icons.description),
            label: 'Informes',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Perfil',
          ),
          NavigationDestination(
            icon: Icon(Icons.menu),
            label: 'Más',
          ),
        ],
      ),
    );
  }
}

class _AppDrawer extends StatelessWidget {
  const _AppDrawer({
    required this.currentLocation,
    required this.user,
    required this.role,
  });

  final String currentLocation;
  final UserModel? user;
  final UserRole? role;

  bool _isPrivileged() =>
      role == UserRole.admin ||
      role == UserRole.secretaria ||
      role == UserRole.directivo;

  @override
  Widget build(BuildContext context) {
    return Drawer(
      backgroundColor: AppColors.sidebar,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Container(
              padding: const EdgeInsets.all(20),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 22,
                    backgroundColor: AppColors.primary.withValues(alpha: 0.2),
                    child: Text(
                      user != null ? _initials(user!.nombre) : '?',
                      style: const TextStyle(
                          color: AppColors.primary, fontWeight: FontWeight.bold),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user?.nombre ?? 'Sadday',
                          style: AppTextStyles.bodyLarge
                              .copyWith(fontWeight: FontWeight.w600),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          _roleLabel(role),
                          style: AppTextStyles.bodySmall
                              .copyWith(color: AppColors.mutedFg),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const Divider(color: AppColors.border, height: 1),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(vertical: 8),
                children: [
                  _DrawerSection(title: 'General', items: [
                    _DrawerItem(
                        icon: Icons.dashboard_outlined,
                        label: 'Dashboard',
                        route: '/dashboard',
                        current: currentLocation),
                  ]),
                  _DrawerSection(title: 'Club', items: [
                    if (_isPrivileged())
                      _DrawerItem(
                          icon: Icons.people_outline,
                          label: 'Socios',
                          route: '/socios',
                          current: currentLocation),
                    _DrawerItem(
                        icon: Icons.landscape_outlined,
                        label: 'Montañas',
                        route: '/montanas',
                        current: currentLocation),
                    _DrawerItem(
                        icon: Icons.route_outlined,
                        label: 'Rutas',
                        route: '/rutas',
                        current: currentLocation),
                    _DrawerItem(
                        icon: Icons.hiking_outlined,
                        label: 'Salidas',
                        route: '/salidas',
                        current: currentLocation),
                    if (_isPrivileged())
                      _DrawerItem(
                          icon: Icons.notifications_outlined,
                          label: 'Notificaciones',
                          route: '/notificaciones',
                          current: currentLocation),
                  ]),
                  _DrawerSection(title: 'Análisis', items: [
                    _DrawerItem(
                        icon: Icons.map_outlined,
                        label: 'Planificador',
                        route: '/planificador',
                        current: currentLocation),
                    _DrawerItem(
                        icon: Icons.bar_chart_outlined,
                        label: 'Estadísticas',
                        route: '/estadisticas',
                        current: currentLocation),
                  ]),
                  _DrawerSection(title: 'Documentos', items: [
                    _DrawerItem(
                        icon: Icons.description_outlined,
                        label: 'Informes',
                        route: '/informes',
                        current: currentLocation),
                    _DrawerItem(
                        icon: Icons.article_outlined,
                        label: 'Actas',
                        route: '/actas',
                        current: currentLocation),
                    _DrawerItem(
                        icon: Icons.gavel_outlined,
                        label: 'Reglamento',
                        route: '/reglamento',
                        current: currentLocation),
                  ]),
                  _DrawerSection(title: 'Teoría', items: [
                    _DrawerItem(
                        icon: Icons.school_outlined,
                        label: 'Teoría',
                        route: '/teoria',
                        current: currentLocation),
                  ]),
                  _DrawerSection(title: 'Sistema', items: [
                    _DrawerItem(
                        icon: Icons.person_outline,
                        label: 'Mi Perfil',
                        route: '/perfil',
                        current: currentLocation),
                    if (role == UserRole.admin || role == UserRole.secretaria)
                      _DrawerItem(
                          icon: Icons.contacts_outlined,
                          label: 'Contactos',
                          route: '/contactos',
                          current: currentLocation),
                    if (role == UserRole.admin ||
                        role == UserRole.secretaria)
                      _DrawerItem(
                          icon: Icons.admin_panel_settings_outlined,
                          label: 'Administración',
                          route: '/admin',
                          current: currentLocation),
                  ]),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _initials(String name) {
    final parts = name.trim().split(' ');
    if (parts.length >= 2) return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    return parts.isNotEmpty ? parts[0][0].toUpperCase() : '?';
  }

  String _roleLabel(UserRole? role) => switch (role) {
        UserRole.admin => 'Admin',
        UserRole.secretaria => 'Secretaria',
        UserRole.directivo => 'Directivo',
        _ => 'Socio',
      };
}

class _DrawerSection extends StatelessWidget {
  const _DrawerSection({required this.title, required this.items});
  final String title;
  final List<Widget> items;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
          child: Text(title,
              style: AppTextStyles.bodySmall.copyWith(
                  color: AppColors.mutedFg, fontWeight: FontWeight.w600)),
        ),
        ...items,
      ],
    );
  }
}

class _DrawerItem extends StatelessWidget {
  const _DrawerItem({
    required this.icon,
    required this.label,
    required this.route,
    required this.current,
  });

  final IconData icon;
  final String label;
  final String route;
  final String current;

  @override
  Widget build(BuildContext context) {
    final selected = current.startsWith(route);
    return ListTile(
      dense: true,
      leading: Icon(icon,
          size: 20,
          color: selected ? AppColors.primary : AppColors.mutedFg),
      title: Text(
        label,
        style: TextStyle(
          color: selected ? AppColors.primary : AppColors.foreground,
          fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
          fontSize: 14,
        ),
      ),
      selected: selected,
      selectedTileColor: AppColors.primary.withValues(alpha: 0.1),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      onTap: () {
        Navigator.pop(context);
        context.go(route);
      },
    );
  }
}
