import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_avatar.dart';
import '../../../../core/widgets/app_badge.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_dialog.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../domain/models/socio_model.dart';
import '../providers/socios_provider.dart';
import 'socios_screen.dart';

class SocioDetailScreen extends ConsumerWidget {
  const SocioDetailScreen({required this.id, super.key});
  final String id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(socioDetailProvider(id));
    return async.when(
      loading: () => const Scaffold(
        backgroundColor: AppColors.background,
        body: Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) => Scaffold(
        backgroundColor: AppColors.background,
        appBar: AppBar(title: const Text('Socio')),
        body: AppEmptyState(
            message: 'Error al cargar', error: e),
      ),
      data: (socio) => _SocioDetailBody(socio: socio),
    );
  }
}

class _SocioDetailBody extends ConsumerWidget {
  const _SocioDetailBody({required this.socio});
  final SocioDetalle socio;

  UserRole? _userRole(WidgetRef ref) {
    final auth = ref.read(authNotifierProvider).asData?.value;
    return auth is AuthAuthenticated ? auth.user.rol : null;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rol = _userRole(ref);
    final canEdit =
        rol == UserRole.admin || rol == UserRole.secretaria;
    final canDelete = rol == UserRole.admin;
    final canHabilitar = rol == UserRole.admin ||
        rol == UserRole.secretaria ||
        rol == UserRole.directivo;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text(socio.nombreCompleto),
        centerTitle: false,
        actions: [
          if (canEdit)
            IconButton(
              icon: const Icon(Icons.edit_outlined),
              onPressed: () => _editSocio(context, ref),
            ),
          IconButton(
            icon: const Icon(Icons.more_vert),
            onPressed: () => _showActions(context, ref,
                canEdit: canEdit,
                canDelete: canDelete,
                canHabilitar: canHabilitar),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async =>
            ref.invalidate(socioDetailProvider(socio.id)),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Header
            AppCard(
              child: Row(
                children: [
                  AppAvatar(name: socio.nombreCompleto, size: 56),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(socio.nombreCompleto,
                            style: AppTextStyles.titleMedium),
                        const SizedBox(height: 4),
                        Text(socio.correo,
                            style: AppTextStyles.bodySmall
                                .copyWith(color: AppColors.mutedFg)),
                        const SizedBox(height: 8),
                        Row(children: [
                          AppBadge(
                              label: socio.rol, color: AppColors.primary),
                          const SizedBox(width: 8),
                          _estadoBadge(socio.estadoHabilitacion),
                        ]),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // Datos personales
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Datos personales',
                      style: AppTextStyles.titleSmall),
                  const SizedBox(height: 12),
                  _InfoRow('Cédula', socio.cedula ?? '—'),
                  _InfoRow('Teléfono', socio.telefono ?? '—'),
                  _InfoRow('Tipo de sangre', socio.sangre ?? '—'),
                  _InfoRow('Tipo de socio', socio.tipoSocio),
                  _InfoRow('Nivel técnico', socio.nivelTecnico ?? '—'),
                  _InfoRow(
                      'Jefe de montaña', socio.esJefeMontana ? 'Sí' : 'No'),
                  if (socio.fechaNacimiento != null)
                    _InfoRow('F. nacimiento', socio.fechaNacimiento!),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // Cuotas
            if (socio.cuotas.isNotEmpty) ...[
              Text('Cuotas', style: AppTextStyles.titleSmall),
              const SizedBox(height: 8),
              ...socio.cuotas.map((c) => _CuotaItem(cuota: c)),
              const SizedBox(height: 12),
            ],

            // Historial habilitación
            if (socio.habilitacionLog.isNotEmpty) ...[
              Text('Historial de habilitación',
                  style: AppTextStyles.titleSmall),
              const SizedBox(height: 8),
              ...socio.habilitacionLog.map((e) => _LogItem(entry: e)),
            ],
          ],
        ),
      ),
    );
  }

  Widget _estadoBadge(String estado) {
    final color = switch (estado) {
      'HABILITADO' || 'VITALICIO' => AppColors.salidaRealizada,
      'INHABILITADO' => AppColors.salidaCancelada,
      _ => AppColors.salidaPlanificada,
    };
    return AppBadge(label: estado, color: color);
  }

  void _editSocio(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => SocioFormSheet(
        socio: socio,
        onSaved: () => ref.invalidate(socioDetailProvider(socio.id)),
      ),
    );
  }

  void _showActions(
    BuildContext context,
    WidgetRef ref, {
    required bool canEdit,
    required bool canDelete,
    required bool canHabilitar,
  }) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.background,
      builder: (_) => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (canHabilitar) ...[
            ListTile(
              leading: const Icon(Icons.check_circle_outline,
                  color: AppColors.salidaRealizada),
              title: const Text('Habilitar'),
              onTap: () async {
                Navigator.pop(context);
                await ref
                    .read(sociosRepositoryProvider)
                    .habilitar(socio.id);
                ref.invalidate(socioDetailProvider(socio.id));
              },
            ),
            ListTile(
              leading: const Icon(Icons.block_outlined,
                  color: AppColors.salidaCancelada),
              title: const Text('Inhabilitar'),
              onTap: () {
                Navigator.pop(context);
                _confirmarInhabilitar(context, ref);
              },
            ),
          ],
          if (canEdit) ...[
            ListTile(
              leading:
                  const Icon(Icons.admin_panel_settings_outlined),
              title: const Text('Cambiar rol'),
              onTap: () {
                Navigator.pop(context);
                _cambiarRol(context, ref);
              },
            ),
            ListTile(
              leading: const Icon(Icons.trending_up_outlined),
              title: const Text('Cambiar nivel técnico'),
              onTap: () {
                Navigator.pop(context);
                _cambiarNivel(context, ref);
              },
            ),
            ListTile(
              leading: const Icon(Icons.landscape_outlined),
              title: Text(socio.esJefeMontana
                  ? 'Remover Jefe de Montaña'
                  : 'Designar Jefe de Montaña'),
              onTap: () async {
                Navigator.pop(context);
                await ref
                    .read(sociosRepositoryProvider)
                    .setJefeMontana(socio.id, !socio.esJefeMontana);
                ref.invalidate(socioDetailProvider(socio.id));
              },
            ),
            ListTile(
              leading: const Icon(Icons.send_outlined),
              title: const Text('Reenviar invitación'),
              onTap: () async {
                Navigator.pop(context);
                await ref
                    .read(sociosRepositoryProvider)
                    .reenviarInvitacion(socio.id);
              },
            ),
            ListTile(
              leading: const Icon(Icons.lock_reset_outlined,
                  color: AppColors.salidaPlanificada),
              title: const Text('Emergency reset (2FA/dispositivo)'),
              onTap: () async {
                Navigator.pop(context);
                await ref
                    .read(sociosRepositoryProvider)
                    .emergencyReset(socio.id);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Reset ejecutado')),
                  );
                }
              },
            ),
          ],
          if (canDelete)
            ListTile(
              leading: const Icon(Icons.delete_outline,
                  color: AppColors.destructive),
              title: const Text('Eliminar socio',
                  style: TextStyle(color: AppColors.destructive)),
              onTap: () {
                Navigator.pop(context);
                _confirmarEliminar(context, ref);
              },
            ),
        ],
      ),
    );
  }

  void _confirmarInhabilitar(BuildContext context, WidgetRef ref) {
    final motivo = TextEditingController();
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.background,
        title: const Text('Inhabilitar socio'),
        content: TextField(
          controller: motivo,
          decoration: const InputDecoration(hintText: 'Motivo'),
          style: const TextStyle(color: AppColors.foreground),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Cancelar')),
          TextButton(
            onPressed: () async {
              Navigator.pop(dialogContext);
              // Nota: el backend no acepta motivo en este endpoint; el motivo
              // queda en el campo de texto pero no se envía.
              await ref.read(sociosRepositoryProvider).inhabilitar(socio.id);
              ref.invalidate(socioDetailProvider(socio.id));
            },
            child: const Text('Inhabilitar',
                style: TextStyle(color: AppColors.destructive)),
          ),
        ],
      ),
    );
  }

  void _confirmarEliminar(BuildContext context, WidgetRef ref) {
    showAppDialog(
      context: context,
      title: 'Eliminar socio',
      message:
          '¿Eliminar a ${socio.nombreCompleto}? Esta acción no se puede deshacer.',
      confirmLabel: 'Eliminar',
      confirmVariant: AppButtonVariant.destructive,
    ).then((ok) async {
      if (ok == true) {
        await ref.read(sociosRepositoryProvider).eliminarSocio(socio.id);
        if (context.mounted) Navigator.pop(context);
      }
    });
  }

  void _cambiarRol(BuildContext context, WidgetRef ref) {
    // El backend requiere rolSistemaId (Short) — se obtiene de /v1/socios/lookups.
    // TODO: cargar lookups y usar el id real al enviar.
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Cambio de rol pendiente de wiring con lookups')),
    );
  }

  void _cambiarNivel(BuildContext context, WidgetRef ref) {
    const niveles = ['BASICO', 'INTERMEDIO', 'AVANZADO', 'EXPERTO'];
    showDialog(
      context: context,
      builder: (dialogContext) => SimpleDialog(
        backgroundColor: AppColors.background,
        title: const Text('Cambiar nivel técnico'),
        children: niveles
            .map((n) => SimpleDialogOption(
                  onPressed: () async {
                    Navigator.pop(dialogContext);
                    await ref
                        .read(sociosRepositoryProvider)
                        .cambiarNivel(socio.id, n);
                    ref.invalidate(socioDetailProvider(socio.id));
                  },
                  child: Text(n,
                      style: TextStyle(
                          color: n == socio.nivelTecnico
                              ? AppColors.primary
                              : AppColors.foreground)),
                ))
            .toList(),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 130,
            child: Text(label,
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
          ),
          Expanded(
            child: Text(value,
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.foreground)),
          ),
        ],
      ),
    );
  }
}

class _CuotaItem extends StatelessWidget {
  const _CuotaItem({required this.cuota});
  final Cuota cuota;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          Icon(
            cuota.pagado
                ? Icons.check_circle
                : Icons.radio_button_unchecked,
            color: cuota.pagado
                ? AppColors.salidaRealizada
                : AppColors.mutedFg,
            size: 18,
          ),
          const SizedBox(width: 8),
          Text(cuota.periodo, style: AppTextStyles.bodyMedium),
          const Spacer(),
          Text('\$${cuota.monto.toStringAsFixed(2)}',
              style: AppTextStyles.bodyMedium
                  .copyWith(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

class _LogItem extends StatelessWidget {
  const _LogItem({required this.entry});
  final HabilitacionLogEntry entry;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd/MM/yyyy HH:mm', 'es');
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 8,
            height: 8,
            margin: const EdgeInsets.only(top: 5, right: 10),
            decoration: BoxDecoration(
              color: entry.estado == 'HABILITADO'
                  ? AppColors.salidaRealizada
                  : AppColors.salidaCancelada,
              shape: BoxShape.circle,
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(entry.estado,
                    style: AppTextStyles.bodySmall
                        .copyWith(fontWeight: FontWeight.w600)),
                if (entry.motivo != null)
                  Text(entry.motivo!,
                      style: AppTextStyles.bodySmall
                          .copyWith(color: AppColors.mutedFg)),
                Text('${entry.actor} · ${df.format(entry.fecha)}',
                    style: const TextStyle(
                        color: AppColors.mutedFg, fontSize: 11)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
