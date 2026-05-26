import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_dialog.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../../core/widgets/app_input.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../domain/models/admin_models.dart';
import '../providers/admin_provider.dart';

class AdminScreen extends ConsumerStatefulWidget {
  const AdminScreen({super.key});

  @override
  ConsumerState<AdminScreen> createState() => _AdminScreenState();
}

class _AdminScreenState extends ConsumerState<AdminScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tab;

  @override
  void initState() {
    super.initState();
    _tab = TabController(length: 4, vsync: this);
  }

  @override
  void dispose() {
    _tab.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Administración'),
        centerTitle: false,
        bottom: TabBar(
          controller: _tab,
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          tabs: const [
            Tab(text: 'Configuración'),
            Tab(text: 'Auditoría'),
            Tab(text: 'Seguridad'),
            Tab(text: 'Usuarios'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tab,
        children: const [
          _ConfigTab(),
          _AuditoriaTab(),
          _SeguridadTab(),
          _UsuariosTab(),
        ],
      ),
    );
  }
}

// ── Tab 1: Configuración ──────────────────────────────────────────────────

class _ConfigTab extends ConsumerWidget {
  const _ConfigTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(adminConfigProvider);
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
        message: 'Error al cargar configuración',
        error: e,
        actionLabel: 'Reintentar',
        onAction: () => ref.invalidate(adminConfigProvider),
      ),
      data: (configs) => RefreshIndicator(
        onRefresh: () async => ref.invalidate(adminConfigProvider),
        child: ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: configs.length,
          itemBuilder: (_, i) => _ConfigItem(
            config: configs[i],
            onSaved: () => ref.invalidate(adminConfigProvider),
          ),
        ),
      ),
    );
  }
}

class _ConfigItem extends ConsumerWidget {
  const _ConfigItem({required this.config, required this.onSaved});
  final AdminConfig config;
  final VoidCallback onSaved;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return AppCard(
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(config.clave,
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
                Text(config.valor,
                    style: AppTextStyles.bodyMedium
                        .copyWith(fontWeight: FontWeight.w500)),
                if (config.descripcion != null)
                  Text(config.descripcion!,
                      style: AppTextStyles.bodySmall
                          .copyWith(color: AppColors.mutedFg)),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.edit_outlined, size: 18),
            onPressed: () => _editConfig(context, ref),
          ),
        ],
      ),
    );
  }

  void _editConfig(BuildContext context, WidgetRef ref) {
    final ctrl = TextEditingController(text: config.valor);
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.background,
        title: Text(config.clave),
        content: TextField(
          controller: ctrl,
          style: const TextStyle(color: AppColors.foreground),
          decoration: const InputDecoration(
            hintText: 'Nuevo valor',
            hintStyle: TextStyle(color: AppColors.mutedFg),
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Cancelar')),
          TextButton(
            onPressed: () async {
              Navigator.pop(dialogContext);
              await ref
                  .read(adminRepositoryProvider)
                  .patchConfig(config.clave, ctrl.text.trim());
              onSaved();
            },
            child: const Text('Guardar'),
          ),
        ],
      ),
    );
  }
}

// ── Tab 2: Auditoría ──────────────────────────────────────────────────────

class _AuditoriaTab extends ConsumerStatefulWidget {
  const _AuditoriaTab();

  @override
  ConsumerState<_AuditoriaTab> createState() => _AuditoriaTabState();
}

class _AuditoriaTabState extends ConsumerState<_AuditoriaTab> {
  int _listKey = 0;
  final _actor = TextEditingController();
  final _entidad = TextEditingController();

  @override
  void dispose() {
    _actor.dispose();
    _entidad.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Row(children: [
            Expanded(
              child: AppInput(
                  controller: _actor,
                  hint: 'Actor',
                  onSubmitted: (_) => setState(() => _listKey++)),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: AppInput(
                  controller: _entidad,
                  hint: 'Entidad',
                  onSubmitted: (_) => setState(() => _listKey++)),
            ),
          ]),
        ),
        Expanded(
          child: AppPagedList<AuditoriaEntry>(
            key: ValueKey('auditoria-$_listKey'),
            loader: (p) => ref.read(adminRepositoryProvider).getAuditoria(
                  page: p,
                  actor:
                      _actor.text.trim().isEmpty ? null : _actor.text.trim(),
                  entidad: _entidad.text.trim().isEmpty
                      ? null
                      : _entidad.text.trim(),
                ),
            emptyMessage: 'Sin registros de auditoría',
            itemBuilder: (_, entry, _) => _AuditoriaRow(entry: entry),
          ),
        ),
      ],
    );
  }
}

class _AuditoriaRow extends StatelessWidget {
  const _AuditoriaRow({required this.entry});
  final AuditoriaEntry entry;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd/MM/yyyy HH:mm', 'es');
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '${entry.accion} · ${entry.entidad}',
                  style: AppTextStyles.bodyMedium
                      .copyWith(fontWeight: FontWeight.w600),
                ),
              ),
              Text(df.format(entry.fecha),
                  style: const TextStyle(
                      color: AppColors.mutedFg, fontSize: 11)),
            ],
          ),
          const SizedBox(height: 4),
          Text('Por: ${entry.actor}',
              style:
                  AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
          if (entry.detalle != null)
            Text(entry.detalle!,
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg),
                maxLines: 2,
                overflow: TextOverflow.ellipsis),
        ],
      ),
    );
  }
}

// ── Tab 3: Eventos de seguridad ───────────────────────────────────────────

class _SeguridadTab extends ConsumerStatefulWidget {
  const _SeguridadTab();

  @override
  ConsumerState<_SeguridadTab> createState() => _SeguridadTabState();
}

class _SeguridadTabState extends ConsumerState<_SeguridadTab> {
  @override
  Widget build(BuildContext context) {
    return AppPagedList<SecurityEvent>(
      loader: (p) =>
          ref.read(adminRepositoryProvider).getSecurityEvents(page: p),
      emptyMessage: 'Sin eventos de seguridad',
      itemBuilder: (_, event, _) => _SecurityEventRow(event: event),
    );
  }
}

class _SecurityEventRow extends StatelessWidget {
  const _SecurityEventRow({required this.event});
  final SecurityEvent event;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd/MM/yyyy HH:mm', 'es');
    final isAlert = event.tipo.contains('FAIL') ||
        event.tipo.contains('BLOCK') ||
        event.tipo.contains('BRUTE');
    return AppCard(
      child: Row(
        children: [
          Icon(
            isAlert ? Icons.warning_amber_outlined : Icons.security_outlined,
            color:
                isAlert ? AppColors.salidaPlanificada : AppColors.mutedFg,
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(event.tipo,
                    style: AppTextStyles.bodySmall
                        .copyWith(fontWeight: FontWeight.w600)),
                Text(event.descripcion,
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
                if (event.usuarioEmail != null)
                  Text(event.usuarioEmail!,
                      style: const TextStyle(
                          color: AppColors.mutedFg, fontSize: 11)),
                Row(children: [
                  if (event.ip != null)
                    Text('IP: ${event.ip!} ',
                        style: const TextStyle(
                            color: AppColors.mutedFg, fontSize: 11)),
                  if (event.pais != null)
                    Text('· ${event.pais!}',
                        style: const TextStyle(
                            color: AppColors.mutedFg, fontSize: 11)),
                ]),
              ],
            ),
          ),
          Text(df.format(event.fecha),
              style:
                  const TextStyle(color: AppColors.mutedFg, fontSize: 11)),
        ],
      ),
    );
  }
}

// ── Tab 4: Usuarios y acceso ──────────────────────────────────────────────

class _UsuariosTab extends ConsumerWidget {
  const _UsuariosTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(adminUsuariosProvider);
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
        message: 'Error al cargar usuarios',
        error: e,
        actionLabel: 'Reintentar',
        onAction: () => ref.invalidate(adminUsuariosProvider),
      ),
      data: (users) => RefreshIndicator(
        onRefresh: () async => ref.invalidate(adminUsuariosProvider),
        child: ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: users.length,
          itemBuilder: (_, i) => _UsuarioAuthItem(user: users[i]),
        ),
      ),
    );
  }
}

class _UsuarioAuthItem extends ConsumerWidget {
  const _UsuarioAuthItem({required this.user});
  final UsuarioAuth user;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isActive = user.estadoAcceso == 'ACTIVE';
    final isBlocked = user.estadoAcceso == 'BLOCKED';
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(user.nombre,
                    style: AppTextStyles.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
              ),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isActive
                      ? AppColors.salidaRealizada.withValues(alpha: 0.15)
                      : AppColors.salidaCancelada.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  user.estadoAcceso,
                  style: TextStyle(
                    color: isActive
                        ? AppColors.salidaRealizada
                        : AppColors.salidaCancelada,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(user.email,
              style:
                  AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
          Text('Rol: ${user.rol}',
              style:
                  const TextStyle(color: AppColors.mutedFg, fontSize: 12)),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            children: [
              if (isBlocked)
                _ActionChip(
                  label: 'Desbloquear',
                  icon: Icons.lock_open_outlined,
                  onTap: () async {
                    await ref
                        .read(adminRepositoryProvider)
                        .desbloquear(user.id);
                    ref.invalidate(adminUsuariosProvider);
                  },
                ),
              _ActionChip(
                label: 'Cerrar sesión',
                icon: Icons.logout_outlined,
                onTap: () => showAppDialog(
                  context: context,
                  title: 'Cerrar sesión',
                  message:
                      '¿Cerrar la sesión activa de ${user.nombre}?',
                  confirmLabel: 'Cerrar sesión',
                  confirmVariant: AppButtonVariant.destructive,
                ).then((ok) async {
                  if (ok == true) {
                    await ref
                        .read(adminRepositoryProvider)
                        .cerrarSesion(user.id);
                  }
                }),
              ),
              _ActionChip(
                label: 'Emergency reset',
                icon: Icons.lock_reset_outlined,
                onTap: () => showAppDialog(
                  context: context,
                  title: 'Emergency reset',
                  message:
                      '¿Resetear 2FA y dispositivos de ${user.nombre}?',
                  confirmLabel: 'Reset',
                  confirmVariant: AppButtonVariant.destructive,
                ).then((ok) async {
                  if (ok == true) {
                    await ref
                        .read(adminRepositoryProvider)
                        .emergencyReset(user.id);
                  }
                }),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ActionChip extends StatelessWidget {
  const _ActionChip({
    required this.label,
    required this.icon,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: AppColors.secondary,
          borderRadius: BorderRadius.circular(6),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 14, color: AppColors.mutedFg),
            const SizedBox(width: 4),
            Text(label,
                style: const TextStyle(
                    color: AppColors.mutedFg,
                    fontSize: 12,
                    fontWeight: FontWeight.w500)),
          ],
        ),
      ),
    );
  }
}
