import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../../core/api/app_exception.dart';
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
import '../../../docs_legales/presentation/providers/docs_legales_provider.dart';
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
    // Jefe de Montaña y Presidenta: solo Admin/Secretaria y únicamente sobre
    // socios con rol DIRECTIVO (idéntico al gating de la web y del backend).
    final canManageRoles =
        (rol == UserRole.admin || rol == UserRole.secretaria) &&
            socio.rol.toUpperCase() == 'DIRECTIVO';

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
                        Wrap(
                          spacing: 8,
                          runSpacing: 4,
                          children: [
                            AppBadge(
                                label: socio.rol, color: AppColors.primary),
                            _estadoBadge(socio.estadoHabilitacion),
                            if (socio.esJefeMontana)
                              const AppBadge(
                                  label: 'Jefe de Montaña',
                                  color: AppColors.salidaPlanificada),
                            if (socio.esPresidenta)
                              const AppBadge(
                                  label: 'Presidenta',
                                  color: AppColors.chart4),
                          ],
                        ),
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
                  if (socio.telefono != null && socio.telefono!.isNotEmpty)
                    _PhoneRow('Teléfono', socio.telefono!)
                  else
                    _InfoRow('Teléfono', '—'),
                  _InfoRow('Dirección', socio.direccion ?? '—'),
                  _InfoRow('Tipo de sangre', socio.sangre ?? '—'),
                  if (socio.fechaNacimiento != null)
                    _InfoRow('F. nacimiento', socio.fechaNacimiento!),
                  if (socio.edad != null)
                    _InfoRow('Edad', '${socio.edad} años'),
                  if (socio.fechaIngreso != null)
                    _InfoRow('F. ingreso', socio.fechaIngreso!),
                  if (socio.fechaSalida != null)
                    _InfoRow('F. salida', socio.fechaSalida!),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // Clasificación
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Clasificación', style: AppTextStyles.titleSmall),
                  const SizedBox(height: 12),
                  _InfoRow('Tipo de socio', socio.tipoSocio),
                  _InfoRow(
                      'Nivel técnico', socio.nivelTecnico ?? 'Sin asignar'),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // Roles directivos — solo Admin/Secretaria sobre socios DIRECTIVO
            if (canManageRoles) ...[
              _RolesDirectivosCard(socio: socio),
              const SizedBox(height: 12),
            ],

            // Contactos de emergencia
            if (socio.emergencyContactName != null ||
                socio.emergencyContactName2 != null) ...[
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Contactos de emergencia',
                        style: AppTextStyles.titleSmall),
                    const SizedBox(height: 12),
                    if (socio.emergencyContactName != null)
                      _EmergencyContact(
                        nombre: socio.emergencyContactName!,
                        telefono: socio.emergencyContactPhone,
                        direccion: socio.emergencyContactDireccion,
                      ),
                    if (socio.emergencyContactName2 != null)
                      _EmergencyContact(
                        nombre: socio.emergencyContactName2!,
                        telefono: socio.emergencyContactPhone2,
                        direccion: socio.emergencyContactDireccion2,
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
            ],

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
            ListTile(
              leading: const Icon(Icons.person_remove_outlined,
                  color: AppColors.destructive),
              title: const Text('Retirar socio',
                  style: TextStyle(color: AppColors.destructive)),
              onTap: () {
                Navigator.pop(context);
                _confirmarRetirar(context, ref);
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

  void _confirmarRetirar(BuildContext context, WidgetRef ref) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _RetireSocioSheet(
        socio: socio,
        onRetired: () {
          ref.invalidate(socioDetailProvider(socio.id));
          Navigator.of(context).pop();
        },
      ),
    );
  }

}

// ── Roles directivos — Jefe de Montaña / Presidenta ────────────────────────
// Solo se muestra para socios con rol DIRECTIVO (gating en el body). El cambio
// de nivel técnico y de rol del sistema se gestionan desde el formulario de
// edición para evitar funcionalidad duplicada.

class _RolesDirectivosCard extends ConsumerStatefulWidget {
  const _RolesDirectivosCard({required this.socio});
  final SocioDetalle socio;

  @override
  ConsumerState<_RolesDirectivosCard> createState() =>
      _RolesDirectivosCardState();
}

class _RolesDirectivosCardState extends ConsumerState<_RolesDirectivosCard> {
  bool _loadingJM = false;
  bool _loadingPres = false;

  Future<void> _toggle({required bool isJM, required bool nuevoValor}) async {
    setState(() {
      if (isJM) {
        _loadingJM = true;
      } else {
        _loadingPres = true;
      }
    });
    try {
      final repo = ref.read(sociosRepositoryProvider);
      if (isJM) {
        await repo.setJefeMontana(widget.socio.id, nuevoValor);
      } else {
        await repo.setPresidenta(widget.socio.id, nuevoValor);
      }
      ref.invalidate(socioDetailProvider(widget.socio.id));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: ${unwrapDio(e)}')),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          if (isJM) {
            _loadingJM = false;
          } else {
            _loadingPres = false;
          }
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final socio = widget.socio;
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Roles directivos', style: AppTextStyles.titleSmall),
          const SizedBox(height: 4),
          Text('Solo para socios con rol Directivo.',
              style: AppTextStyles.bodySmall
                  .copyWith(color: AppColors.mutedFg)),
          const SizedBox(height: 12),
          _RolToggleRow(
            label: 'Jefe de Montaña',
            activo: socio.esJefeMontana,
            activeColor: AppColors.salidaPlanificada,
            loading: _loadingJM,
            onPressed: () =>
                _toggle(isJM: true, nuevoValor: !socio.esJefeMontana),
          ),
          const SizedBox(height: 8),
          _RolToggleRow(
            label: 'Presidenta',
            activo: socio.esPresidenta,
            activeColor: AppColors.chart4,
            loading: _loadingPres,
            onPressed: () =>
                _toggle(isJM: false, nuevoValor: !socio.esPresidenta),
          ),
        ],
      ),
    );
  }
}

class _RolToggleRow extends StatelessWidget {
  const _RolToggleRow({
    required this.label,
    required this.activo,
    required this.activeColor,
    required this.loading,
    required this.onPressed,
  });

  final String label;
  final bool activo;
  final Color activeColor;
  final bool loading;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        border: Border.all(color: AppColors.border),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label,
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
                const SizedBox(height: 2),
                Text(
                  activo ? 'Activo' : 'No asignado',
                  style: AppTextStyles.bodyMedium.copyWith(
                    fontWeight: FontWeight.w600,
                    color: activo ? activeColor : AppColors.mutedFg,
                  ),
                ),
              ],
            ),
          ),
          AppButton(
            label: activo ? 'Quitar' : 'Asignar',
            variant: activo
                ? AppButtonVariant.destructive
                : AppButtonVariant.secondary,
            loading: loading,
            onPressed: onPressed,
          ),
        ],
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

// ── Teléfonos accionables (copiar / llamar / WhatsApp) ─────────────────────

/// Normaliza un teléfono para wa.me. Asume Ecuador: convierte 09######## a
/// 5939######## cuando aplica; en otro caso usa solo los dígitos.
String _waNumber(String phone) {
  var d = phone.replaceAll(RegExp(r'\D'), '');
  if (d.length == 10 && d.startsWith('0')) d = '593${d.substring(1)}';
  return d;
}

void _showPhoneActions(BuildContext context, String phone) {
  showModalBottomSheet(
    context: context,
    backgroundColor: AppColors.background,
    builder: (sheetCtx) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
            child: Row(
              children: [
                const Icon(Icons.phone_outlined,
                    size: 18, color: AppColors.mutedFg),
                const SizedBox(width: 8),
                Text(phone,
                    style: AppTextStyles.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
              ],
            ),
          ),
          ListTile(
            leading:
                const Icon(Icons.call_outlined, color: AppColors.primary),
            title: const Text('Llamar'),
            onTap: () async {
              Navigator.pop(sheetCtx);
              await launchUrl(Uri(scheme: 'tel', path: phone));
            },
          ),
          ListTile(
            leading: const Icon(Icons.chat_outlined,
                color: AppColors.salidaRealizada),
            title: const Text('Abrir en WhatsApp'),
            onTap: () async {
              Navigator.pop(sheetCtx);
              final uri = Uri.parse('https://wa.me/${_waNumber(phone)}');
              final ok =
                  await launchUrl(uri, mode: LaunchMode.externalApplication);
              if (!ok && context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('No se pudo abrir WhatsApp')),
                );
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.copy_outlined),
            title: const Text('Copiar número'),
            onTap: () {
              Navigator.pop(sheetCtx);
              Clipboard.setData(ClipboardData(text: phone));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Número copiado')),
              );
            },
          ),
        ],
      ),
    ),
  );
}

/// Texto de teléfono pulsable que abre el menú de acciones.
class _TappablePhone extends StatelessWidget {
  const _TappablePhone(this.phone);
  final String phone;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => _showPhoneActions(context, phone),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Flexible(
            child: Text(phone,
                style: AppTextStyles.bodySmall.copyWith(
                  color: AppColors.primary,
                  decoration: TextDecoration.underline,
                  decorationColor: AppColors.primary,
                )),
          ),
          const SizedBox(width: 6),
          const Icon(Icons.more_horiz, size: 16, color: AppColors.mutedFg),
        ],
      ),
    );
  }
}

/// Fila etiqueta + teléfono pulsable, con el mismo layout que [_InfoRow].
class _PhoneRow extends StatelessWidget {
  const _PhoneRow(this.label, this.phone);
  final String label;
  final String phone;

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
          Expanded(child: _TappablePhone(phone)),
        ],
      ),
    );
  }
}

/// Contacto de emergencia: nombre + teléfono pulsable + dirección.
class _EmergencyContact extends StatelessWidget {
  const _EmergencyContact({
    required this.nombre,
    this.telefono,
    this.direccion,
  });
  final String nombre;
  final String? telefono;
  final String? direccion;

  @override
  Widget build(BuildContext context) {
    final tel = telefono;
    final dir = direccion;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(nombre,
              style: AppTextStyles.bodyMedium
                  .copyWith(fontWeight: FontWeight.w600)),
          if (tel != null && tel.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: _TappablePhone(tel),
            ),
          if (dir != null && dir.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(dir,
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg)),
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

// ── Retire socio sheet ────────────────────────────────────────────────────────

class _RetireSocioSheet extends ConsumerStatefulWidget {
  const _RetireSocioSheet({required this.socio, required this.onRetired});
  final SocioDetalle socio;
  final VoidCallback onRetired;

  @override
  ConsumerState<_RetireSocioSheet> createState() => _RetireSocioSheetState();
}

class _RetireSocioSheetState extends ConsumerState<_RetireSocioSheet> {
  final _confirmCtrl = TextEditingController();
  final _reasonCtrl = TextEditingController();
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _confirmCtrl.dispose();
    _reasonCtrl.dispose();
    super.dispose();
  }

  String get _expectedText =>
      'Si, deseo eliminar al socio ${widget.socio.nombreCompleto}';

  bool get _valid =>
      _confirmCtrl.text == _expectedText &&
      _reasonCtrl.text.trim().isNotEmpty;

  Future<void> _retire() async {
    if (!_valid) return;
    setState(() { _saving = true; _error = null; });
    try {
      await ref.read(docsLegalesRepositoryProvider).retireSocio(
            socioId: widget.socio.id,
            reason: _reasonCtrl.text.trim(),
          );
      widget.onRetired();
    } catch (e) {
      setState(() => _error = unwrapDio(e).toString());
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
            Center(
              child: Container(
                width: 36, height: 4,
                decoration: BoxDecoration(
                  color: AppColors.border,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Retirar socio', style: AppTextStyles.titleMedium
                    .copyWith(color: AppColors.destructive)),
                IconButton(
                  icon: const Icon(Icons.close, size: 20),
                  onPressed: () => Navigator.of(context).pop(),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  color: AppColors.mutedFg,
                ),
              ],
            ),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.destructive.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                    color: AppColors.destructive.withValues(alpha: 0.3)),
              ),
              child: Text(
                'Esta acción retirará permanentemente al socio del sistema. '
                'No se puede deshacer.',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.destructive),
              ),
            ),
            const SizedBox(height: 20),
            Text('Motivo del retiro *',
                style: AppTextStyles.bodyMedium
                    .copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            TextField(
              controller: _reasonCtrl,
              maxLines: 3,
              maxLength: 500,
              style: const TextStyle(color: AppColors.foreground),
              decoration: const InputDecoration(
                hintText: 'Describe el motivo del retiro',
                hintStyle: TextStyle(color: AppColors.mutedFg),
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 16),
            Text('Confirmación *',
                style: AppTextStyles.bodyMedium
                    .copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 4),
            Text(
              'Para confirmar, escribe exactamente:',
              style: AppTextStyles.bodySmall
                  .copyWith(color: AppColors.mutedFg),
            ),
            const SizedBox(height: 6),
            Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.secondary,
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: AppColors.border),
              ),
              child: Text(
                _expectedText,
                style: const TextStyle(
                    fontFamily: 'monospace',
                    fontSize: 13,
                    color: AppColors.foreground),
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _confirmCtrl,
              style: const TextStyle(color: AppColors.foreground),
              decoration: const InputDecoration(
                hintText: 'Escribe el texto exacto',
                hintStyle: TextStyle(color: AppColors.mutedFg),
              ),
              onChanged: (_) => setState(() {}),
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!,
                  style: const TextStyle(color: AppColors.destructive)),
            ],
            const SizedBox(height: 24),
            AppButton(
              label: 'Retirar socio',
              variant: AppButtonVariant.destructive,
              loading: _saving,
              onPressed: _valid ? _retire : null,
            ),
          ],
        ),
      ),
    );
  }
}

// ── Habilitación log ──────────────────────────────────────────────────────────

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
