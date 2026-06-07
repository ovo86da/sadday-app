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
import '../../../docs_legales/domain/models/docs_legales_models.dart';
import '../../../docs_legales/presentation/providers/docs_legales_provider.dart';
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
    _tab = TabController(length: 5, vsync: this);
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
            Tab(text: 'Documentos'),
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
          _DocumentosTab(),
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

// ── Tab 5: Documentos legales ─────────────────────────────────────────────────

class _DocumentosTab extends ConsumerStatefulWidget {
  const _DocumentosTab();

  @override
  ConsumerState<_DocumentosTab> createState() => _DocumentosTabState();
}

class _DocumentosTabState extends ConsumerState<_DocumentosTab> {
  final Set<String> _expanded = {};

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(adminDocsProvider);
    final pendingAsync = ref.watch(pendingAcceptancesProvider);

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(adminDocsProvider);
        ref.invalidate(pendingAcceptancesProvider);
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Pending acceptances banner ─────────────────────────────────
          pendingAsync.when(
            loading: () => const SizedBox.shrink(),
            error: (_, _) => const SizedBox.shrink(),
            data: (pending) => pending.isEmpty
                ? const SizedBox.shrink()
                : Container(
                    margin: const EdgeInsets.only(bottom: 16),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color:
                          AppColors.salidaPlanificada.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                          color: AppColors.salidaPlanificada
                              .withValues(alpha: 0.3)),
                    ),
                    child: Row(children: [
                      const Icon(Icons.pending_outlined,
                          color: AppColors.salidaPlanificada, size: 18),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '${pending.length} socio(s) con documentos pendientes de aceptación',
                          style: AppTextStyles.bodySmall.copyWith(
                              color: AppColors.salidaPlanificada),
                        ),
                      ),
                    ]),
                  ),
          ),

          // ── Header with create button ──────────────────────────────────
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Documentos legales',
                  style: AppTextStyles.titleMedium
                      .copyWith(fontWeight: FontWeight.w600)),
              AppButton(
                label: 'Nuevo',
                icon: Icons.add,
                variant: AppButtonVariant.secondary,
                onPressed: () => _showCreateDocSheet(context),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // ── Docs list grouped by code ──────────────────────────────────
          async.when(
            loading: () =>
                const Center(child: CircularProgressIndicator()),
            error: (e, _) => AppEmptyState(
              message: 'Error al cargar documentos',
              error: e,
              actionLabel: 'Reintentar',
              onAction: () => ref.invalidate(adminDocsProvider),
            ),
            data: (docs) {
              if (docs.isEmpty) {
                return const AppEmptyState(
                    message: 'No hay documentos creados',
                    icon: Icons.description_outlined);
              }
              // Group by code
              final groups = <String, List<LegalDoc>>{};
              for (final d in docs) {
                groups.putIfAbsent(d.code, () => []).add(d);
              }
              // Sort each group by version desc
              for (final g in groups.values) {
                g.sort((a, b) => b.version.compareTo(a.version));
              }
              return Column(
                children: groups.entries.map((entry) {
                  final latest = entry.value.first;
                  final expanded = _expanded.contains(entry.key);
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: AppCard(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          // Header row
                          Row(children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(latest.title,
                                      style: AppTextStyles.bodyMedium.copyWith(
                                          fontWeight: FontWeight.w600)),
                                  Text('${latest.code} · ${latest.documentType}',
                                      style: AppTextStyles.bodySmall
                                          .copyWith(color: AppColors.mutedFg)),
                                ],
                              ),
                            ),
                            _StatusBadge(active: latest.active),
                            IconButton(
                              icon: Icon(
                                expanded
                                    ? Icons.expand_less
                                    : Icons.expand_more,
                                size: 20,
                                color: AppColors.mutedFg,
                              ),
                              onPressed: () => setState(() {
                                if (expanded) {
                                  _expanded.remove(entry.key);
                                } else {
                                  _expanded.add(entry.key);
                                }
                              }),
                            ),
                          ]),
                          const SizedBox(height: 8),
                          // Action row for latest
                          Wrap(spacing: 8, children: [
                            _ActionChip(
                              label: 'Nueva versión',
                              icon: Icons.add_circle_outline,
                              onTap: () => _showNewVersionSheet(
                                  context, latest),
                            ),
                            if (!latest.active)
                              _ActionChip(
                                label: 'Activar',
                                icon: Icons.check_circle_outline,
                                onTap: () => _activar(latest),
                              ),
                            _ActionChip(
                              label: 'Aceptaciones',
                              icon: Icons.people_outline,
                              onTap: () =>
                                  _showAcceptances(context, latest),
                            ),
                          ]),
                          // History rows
                          if (expanded) ...[
                            const Divider(
                                height: 16, color: AppColors.border),
                            Text('Historial de versiones',
                                style: AppTextStyles.labelSmall
                                    .copyWith(color: AppColors.mutedFg)),
                            const SizedBox(height: 6),
                            ...entry.value.map((d) => Padding(
                                  padding:
                                      const EdgeInsets.only(bottom: 4),
                                  child: Row(children: [
                                    Text('v${d.version}',
                                        style: AppTextStyles.bodySmall
                                            .copyWith(
                                                fontWeight:
                                                    FontWeight.w600)),
                                    const SizedBox(width: 8),
                                    _StatusBadge(active: d.active),
                                    const Spacer(),
                                    if (d.approvedAt != null)
                                      Text(
                                        DateFormat('dd/MM/yyyy')
                                            .format(d.approvedAt!),
                                        style: const TextStyle(
                                            color: AppColors.mutedFg,
                                            fontSize: 11),
                                      ),
                                  ]),
                                )),
                          ],
                        ],
                      ),
                    ),
                  );
                }).toList(),
              );
            },
          ),
        ],
      ),
    );
  }

  Future<void> _activar(LegalDoc doc) async {
    final ok = await showAppDialog(
      context: context,
      title: 'Activar documento',
      message:
          '¿Activar "${doc.title}" v${doc.version}? Esto lo marcará como la versión vigente.',
      confirmLabel: 'Activar',
    );
    if (ok != true || !mounted) return;
    try {
      await ref.read(docsLegalesRepositoryProvider).activateDoc(doc.id);
      ref.invalidate(adminDocsProvider);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: $e'),
          backgroundColor: AppColors.destructive,
        ));
      }
    }
  }

  void _showCreateDocSheet(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _CreateDocSheet(
        onCreated: () => ref.invalidate(adminDocsProvider),
      ),
    );
  }

  void _showNewVersionSheet(BuildContext context, LegalDoc doc) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _NewVersionSheet(
        doc: doc,
        onCreated: () => ref.invalidate(adminDocsProvider),
      ),
    );
  }

  void _showAcceptances(BuildContext context, LegalDoc doc) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _DocAcceptancesSheet(doc: doc),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.active});
  final bool active;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: active
              ? AppColors.salidaRealizada.withValues(alpha: 0.15)
              : AppColors.mutedFg.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(4),
        ),
        child: Text(
          active ? 'Activo' : 'Inactivo',
          style: TextStyle(
            color: active
                ? AppColors.salidaRealizada
                : AppColors.mutedFg,
            fontSize: 11,
            fontWeight: FontWeight.w600,
          ),
        ),
      );
}

// ── Create doc sheet ──────────────────────────────────────────────────────────

const _docTypes = [
  'TERMS_AND_CONDITIONS',
  'PRIVACY_POLICY',
  'LIABILITY_WAIVER',
  'DATA_PROCESSING_POLICY',
  'DATA_RETENTION_POLICY',
  'MEDICAL_DATA_CONSENT',
  'MEMBERSHIP_AGREEMENT',
];

const _stages = [
  'REGISTRO',
  'ACTIVIDAD',
  'MEMBRESIA',
];

class _CreateDocSheet extends ConsumerStatefulWidget {
  const _CreateDocSheet({required this.onCreated});
  final VoidCallback onCreated;

  @override
  ConsumerState<_CreateDocSheet> createState() => _CreateDocSheetState();
}

class _CreateDocSheetState extends ConsumerState<_CreateDocSheet> {
  final _code = TextEditingController();
  final _title = TextEditingController();
  final _content = TextEditingController();
  String? _docType;
  String? _stage;
  bool _required = true;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _code.dispose();
    _title.dispose();
    _content.dispose();
    super.dispose();
  }

  bool get _valid =>
      _code.text.trim().isNotEmpty &&
      _title.text.trim().isNotEmpty &&
      _content.text.trim().isNotEmpty &&
      _docType != null &&
      _stage != null;

  Future<void> _save() async {
    if (!_valid) return;
    setState(() { _saving = true; _error = null; });
    try {
      await ref.read(docsLegalesRepositoryProvider).createDoc({
        'code': _code.text.trim(),
        'title': _title.text.trim(),
        'content': _content.text.trim(),
        'documentType': _docType!,
        'requiredStage': _stage!,
        'required': _required,
      });
      widget.onCreated();
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            _handle(),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Crear documento', style: AppTextStyles.titleMedium),
                _closeBtn(context),
              ],
            ),
            const SizedBox(height: 16),
            AppInput(label: 'Código único *', controller: _code),
            const SizedBox(height: 10),
            AppInput(label: 'Título *', controller: _title),
            const SizedBox(height: 10),
            _DropdownField(
              label: 'Tipo de documento *',
              value: _docType,
              options: _docTypes,
              onChanged: (v) => setState(() => _docType = v),
            ),
            const SizedBox(height: 10),
            _DropdownField(
              label: 'Etapa requerida *',
              value: _stage,
              options: _stages,
              onChanged: (v) => setState(() => _stage = v),
            ),
            const SizedBox(height: 10),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.secondary,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: AppColors.border),
              ),
              child: Row(children: [
                Expanded(
                    child: Text('Obligatorio',
                        style: AppTextStyles.bodyMedium)),
                Switch(
                  value: _required,
                  activeThumbColor: AppColors.primary,
                  onChanged: (v) => setState(() => _required = v),
                ),
              ]),
            ),
            const SizedBox(height: 10),
            AppInput(
                label: 'Contenido (Markdown) *',
                controller: _content,
                maxLines: 8),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!,
                  style: const TextStyle(color: AppColors.destructive)),
            ],
            const SizedBox(height: 20),
            AppButton(
              label: 'Crear documento',
              loading: _saving,
              onPressed: _valid ? _save : null,
            ),
          ],
        ),
      ),
    );
  }
}

// ── New version sheet ─────────────────────────────────────────────────────────

class _NewVersionSheet extends ConsumerStatefulWidget {
  const _NewVersionSheet({required this.doc, required this.onCreated});
  final LegalDoc doc;
  final VoidCallback onCreated;

  @override
  ConsumerState<_NewVersionSheet> createState() => _NewVersionSheetState();
}

class _NewVersionSheetState extends ConsumerState<_NewVersionSheet> {
  late final TextEditingController _content;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _content = TextEditingController(text: widget.doc.content ?? '');
  }

  @override
  void dispose() {
    _content.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_content.text.trim().isEmpty) return;
    setState(() { _saving = true; _error = null; });
    try {
      await ref.read(docsLegalesRepositoryProvider).createNewVersion(
            docId: widget.doc.id,
            content: _content.text.trim(),
          );
      widget.onCreated();
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            _handle(),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Nueva versión',
                          style: AppTextStyles.titleMedium),
                      Text(
                        '${widget.doc.title} · v${widget.doc.version + 1}',
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg),
                      ),
                    ],
                  ),
                ),
                _closeBtn(context),
              ],
            ),
            const SizedBox(height: 16),
            AppInput(
              label: 'Contenido (Markdown) *',
              controller: _content,
              maxLines: 12,
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!,
                  style: const TextStyle(color: AppColors.destructive)),
            ],
            const SizedBox(height: 20),
            AppButton(label: 'Crear versión', loading: _saving, onPressed: _save),
          ],
        ),
      ),
    );
  }
}

// ── Doc acceptances sheet ─────────────────────────────────────────────────────

class _DocAcceptancesSheet extends ConsumerWidget {
  const _DocAcceptancesSheet({required this.doc});
  final LegalDoc doc;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(docAcceptancesProvider(doc.id));
    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.6,
      maxChildSize: 0.9,
      builder: (_, ctrl) => Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(child: _handle()),
                const SizedBox(height: 12),
                Text('Aceptaciones — ${doc.title}',
                    style: AppTextStyles.titleMedium),
                Text('v${doc.version}',
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
              ],
            ),
          ),
          const Divider(height: 1, color: AppColors.border),
          Expanded(
            child: async.when(
              loading: () =>
                  const Center(child: CircularProgressIndicator()),
              error: (e, _) =>
                  AppEmptyState(message: 'Error al cargar', error: e),
              data: (list) => list.isEmpty
                  ? const AppEmptyState(
                      message: 'Ningún socio ha aceptado este documento',
                      icon: Icons.people_outline)
                  : ListView.builder(
                      controller: ctrl,
                      padding: const EdgeInsets.all(16),
                      itemCount: list.length,
                      itemBuilder: (_, i) {
                        final item = list[i];
                        return AppCard(
                          child: Row(children: [
                            const Icon(Icons.check_circle_outline,
                                size: 18,
                                color: AppColors.salidaRealizada),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment:
                                    CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    item['socioNombre']?.toString() ??
                                        '—',
                                    style: AppTextStyles.bodyMedium,
                                  ),
                                  if (item['acceptedAt'] != null)
                                    Text(
                                      DateFormat('dd/MM/yyyy HH:mm').format(
                                          DateTime.parse(item['acceptedAt']
                                              .toString())),
                                      style: AppTextStyles.bodySmall
                                          .copyWith(
                                              color: AppColors.mutedFg),
                                    ),
                                ],
                              ),
                            ),
                          ]),
                        );
                      },
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Shared sheet helpers ──────────────────────────────────────────────────────

Widget _handle() => Center(
      child: Container(
        width: 36,
        height: 4,
        decoration: BoxDecoration(
          color: AppColors.border,
          borderRadius: BorderRadius.circular(2),
        ),
      ),
    );

Widget _closeBtn(BuildContext context) => IconButton(
      icon: const Icon(Icons.close, size: 20),
      onPressed: () => Navigator.of(context).pop(),
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(),
      color: AppColors.mutedFg,
    );

class _DropdownField extends StatelessWidget {
  const _DropdownField({
    required this.label,
    required this.value,
    required this.options,
    required this.onChanged,
  });
  final String label;
  final String? value;
  final List<String> options;
  final void Function(String?) onChanged;

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: () async {
          final sel = await showModalBottomSheet<String>(
            context: context,
            backgroundColor: AppColors.background,
            shape: const RoundedRectangleBorder(
              borderRadius:
                  BorderRadius.vertical(top: Radius.circular(16)),
            ),
            builder: (_) => ListView(
              shrinkWrap: true,
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
              children: [
                Center(child: _handle()),
                const SizedBox(height: 12),
                Text(label, style: AppTextStyles.titleMedium),
                const SizedBox(height: 8),
                ...options.map((o) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(o),
                      trailing: o == value
                          ? const Icon(Icons.check,
                              color: AppColors.primary)
                          : null,
                      onTap: () => Navigator.pop(context, o),
                    )),
              ],
            ),
          );
          if (sel != null) onChanged(sel);
        },
        child: Container(
          padding:
              const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
          decoration: BoxDecoration(
            color: AppColors.secondary,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: AppColors.border),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(label,
                        style: AppTextStyles.labelSmall
                            .copyWith(color: AppColors.mutedFg)),
                    const SizedBox(height: 2),
                    Text(
                      value ?? 'Seleccionar',
                      style: AppTextStyles.bodyMedium.copyWith(
                        color: value != null
                            ? AppColors.foreground
                            : AppColors.mutedFg,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right,
                  size: 18, color: AppColors.mutedFg),
            ],
          ),
        ),
      );
}
