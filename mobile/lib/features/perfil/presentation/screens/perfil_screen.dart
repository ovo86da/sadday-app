import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_avatar.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../domain/models/perfil_model.dart';
import '../providers/perfil_provider.dart';

class PerfilScreen extends ConsumerStatefulWidget {
  const PerfilScreen({super.key});

  @override
  ConsumerState<PerfilScreen> createState() => _PerfilScreenState();
}

class _PerfilScreenState extends ConsumerState<PerfilScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final asyncPerfil = ref.watch(perfilNotifierProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Mi Perfil'),
        centerTitle: false,
        actions: [
          TextButton.icon(
            icon: const Icon(Icons.logout, size: 18),
            label: const Text('Salir'),
            style: TextButton.styleFrom(
                foregroundColor: AppColors.destructive),
            onPressed: () => _logout(context),
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Datos'),
            Tab(text: 'Seguridad'),
          ],
        ),
      ),
      body: asyncPerfil.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => AppEmptyState(
          message: 'Error al cargar el perfil',
          description: e.toString(),
          actionLabel: 'Reintentar',
          onAction: () => ref.invalidate(perfilNotifierProvider),
        ),
        data: (perfil) => TabBarView(
          controller: _tabController,
          children: [
            _DatosTab(perfil: perfil),
            const _SeguridadTab(),
          ],
        ),
      ),
    );
  }

  Future<void> _logout(BuildContext context) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.sidebar,
        title: const Text('Cerrar sesión'),
        content: const Text('¿Deseas cerrar tu sesión?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Cancelar')),
          TextButton(
              style: TextButton.styleFrom(foregroundColor: AppColors.destructive),
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Cerrar sesión')),
        ],
      ),
    );
    if (confirm == true) {
      // logout() cambia el auth state a AuthUnauthenticated; el router
      // redirige a /login automáticamente (ver _AuthRefreshListenable +
      // authRedirect). No navegar manualmente: evita una doble navegación
      // que deja el stack de go_router vacío (pantalla negra).
      await ref.read(authNotifierProvider.notifier).logout();
    }
  }
}

class _DatosTab extends ConsumerWidget {
  const _DatosTab({required this.perfil});
  final PerfilSocio perfil;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Avatar + nombre
        Center(
          child: Column(
            children: [
              AppAvatar(name: perfil.nombreCompleto, size: 72),
              const SizedBox(height: 12),
              Text(perfil.nombreCompleto,
                  style: AppTextStyles.headlineMedium
                      .copyWith(fontWeight: FontWeight.bold)),
              if (perfil.rol != null)
                Text(perfil.rol!,
                    style:
                        AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg)),
              if (perfil.nivelTecnico != null) ...[
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                  decoration: BoxDecoration(
                    color: AppColors.primary.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                        color: AppColors.primary.withValues(alpha: 0.3)),
                  ),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    const Icon(Icons.signal_cellular_alt,
                        size: 14, color: AppColors.primary),
                    const SizedBox(width: 6),
                    Text('Nivel ${perfil.nivelTecnico!}',
                        style: AppTextStyles.bodyMedium.copyWith(
                            color: AppColors.primary,
                            fontWeight: FontWeight.w700)),
                  ]),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Datos personales (readonly)
        Text('Datos personales',
            style:
                AppTextStyles.titleMedium.copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        AppCard(
          child: Column(
            children: [
              if (perfil.cedula != null) _ReadonlyField('Cédula', perfil.cedula!),
              _ReadonlyField('Correo', perfil.correo ?? '—'),
              if (perfil.telefono != null)
                _ReadonlyField('Teléfono', perfil.telefono!),
              if (perfil.tipoSangre != null)
                _ReadonlyField('Tipo de sangre', perfil.tipoSangre!),
              if (perfil.direccion != null)
                _ReadonlyField('Dirección', perfil.direccion!),
              if (perfil.tipoSocio != null)
                _ReadonlyField('Tipo de socio', perfil.tipoSocio!),
              if (perfil.estadoHabilitacion != null)
                _ReadonlyField('Estado', perfil.estadoHabilitacion!),
            ],
          ),
        ),
        const SizedBox(height: 16),

        // Contactos de emergencia
        if (perfil.contactosEmergencia.isNotEmpty) ...[
          Text('Contactos de emergencia',
              style: AppTextStyles.titleMedium
                  .copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          AppCard(
            child: Column(
              children: perfil.contactosEmergencia
                  .map((c) => ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.contact_phone_outlined,
                            color: AppColors.primary, size: 20),
                        title: Text(c.nombre, style: AppTextStyles.bodyMedium),
                        subtitle: Text(c.telefono,
                            style: AppTextStyles.bodySmall
                                .copyWith(color: AppColors.mutedFg)),
                        trailing: c.direccion != null
                            ? SizedBox(
                                width: 120,
                                child: Text(c.direccion!,
                                    textAlign: TextAlign.end,
                                    overflow: TextOverflow.ellipsis,
                                    style: AppTextStyles.labelSmall
                                        .copyWith(color: AppColors.mutedFg)),
                              )
                            : null,
                      ))
                  .toList(),
            ),
          ),
        ],
      ],
    );
  }
}

class _ReadonlyField extends StatelessWidget {
  const _ReadonlyField(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(children: [
          SizedBox(
            width: 110,
            child: Text(label,
                style:
                    AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
          ),
          Expanded(child: Text(value, style: AppTextStyles.bodyMedium)),
        ]),
      );
}

class _SeguridadTab extends ConsumerStatefulWidget {
  const _SeguridadTab();

  @override
  ConsumerState<_SeguridadTab> createState() => _SeguridadTabState();
}

class _SeguridadTabState extends ConsumerState<_SeguridadTab> {
  bool _biometricEnabled = false;

  @override
  Widget build(BuildContext context) {
    final sesionesAsync = ref.watch(sesionesProvider);
    final mfaAsync = ref.watch(mfaStatusProvider);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // 2FA
        Text('Autenticación de dos factores',
            style:
                AppTextStyles.titleMedium.copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        AppCard(
          child: mfaAsync.when(
            loading: () => const CircularProgressIndicator(),
            error: (_, _) => const Text('Error al cargar estado MFA'),
            data: (enabled) => ListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('2FA TOTP'),
              subtitle: Text(
                  enabled ? 'Habilitado' : 'Deshabilitado',
                  style: TextStyle(
                      color: enabled
                          ? AppColors.salidaRealizada
                          : AppColors.mutedFg)),
              trailing: AppButton(
                label: enabled ? 'Desactivar' : 'Activar',
                onPressed: () => _toggleMfa(context, enabled),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Biometría
        Text('Biometría',
            style:
                AppTextStyles.titleMedium.copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        AppCard(
          child: SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Acceso con huella / Face ID'),
            value: _biometricEnabled,
            activeThumbColor: AppColors.primary,
            onChanged: (v) => setState(() => _biometricEnabled = v),
          ),
        ),
        const SizedBox(height: 16),

        // Sesiones activas
        Text('Sesiones activas',
            style:
                AppTextStyles.titleMedium.copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        sesionesAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text(e.toString(),
              style: const TextStyle(color: AppColors.mutedFg)),
          data: (sesiones) => AppCard(
            child: Column(
              children: [
                ...sesiones.map((s) => _SesionItem(
                      sesion: s,
                      onCerrar: () async {
                        await ref.read(perfilRepositoryProvider).cerrarSesion(s.id);
                        ref.invalidate(sesionesProvider);
                      },
                    )),
                if (sesiones.length > 1) ...[
                  const Divider(color: AppColors.border),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Cerrar todas las demás sesiones',
                        style: TextStyle(
                            color: AppColors.destructive, fontSize: 14)),
                    trailing: const Icon(Icons.logout,
                        color: AppColors.destructive, size: 18),
                    onTap: () async {
                      await ref
                          .read(perfilRepositoryProvider)
                          .cerrarOtrasSesiones();
                      ref.invalidate(sesionesProvider);
                    },
                  ),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _toggleMfa(BuildContext context, bool enabled) async {
    if (!enabled) {
      // Setup MFA — mostrar QR
      final qrUri = await ref.read(perfilRepositoryProvider).setupMfa();
      if (!context.mounted) return;
      await showDialog(
        context: context,
        builder: (_) => _MfaSetupDialog(qrUri: qrUri, ref: ref),
      );
      ref.invalidate(mfaStatusProvider);
    } else {
      // Deshabilitar MFA
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content: Text('Ingresa tu código TOTP actual para deshabilitar 2FA')),
      );
    }
  }
}

class _SesionItem extends StatelessWidget {
  const _SesionItem({required this.sesion, required this.onCerrar});
  final SesionActiva sesion;
  final VoidCallback onCerrar;

  @override
  Widget build(BuildContext context) {
    final icon = sesion.plataforma?.toUpperCase() == 'MOBILE'
        ? Icons.phone_android
        : Icons.computer;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(icon, color: AppColors.mutedFg, size: 20),
      title: Text(
          '${sesion.plataforma ?? 'Desconocido'} · ${sesion.ip ?? '—'}',
          style: AppTextStyles.bodySmall),
      subtitle: sesion.ultimaActividad != null
          ? Text(
              sesion.ultimaActividad!.toLocal().toString().substring(0, 16),
              style: AppTextStyles.labelSmall
                  .copyWith(color: AppColors.mutedFg))
          : null,
      trailing: sesion.esCurrent
          ? const Text('Actual',
              style: TextStyle(color: AppColors.salidaRealizada, fontSize: 12))
          : IconButton(
              icon: const Icon(Icons.close,
                  color: AppColors.destructive, size: 18),
              onPressed: onCerrar,
            ),
    );
  }
}

class _MfaSetupDialog extends StatefulWidget {
  const _MfaSetupDialog({required this.qrUri, required this.ref});
  final String qrUri;
  final WidgetRef ref;

  @override
  State<_MfaSetupDialog> createState() => _MfaSetupDialogState();
}

class _MfaSetupDialogState extends State<_MfaSetupDialog> {
  final _codeController = TextEditingController();
  bool _loading = false;

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.sidebar,
      title: const Text('Activar 2FA'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('Escanea este código QR con tu app autenticadora:',
              style: TextStyle(color: AppColors.mutedFg, fontSize: 13)),
          const SizedBox(height: 12),
          if (widget.qrUri.isNotEmpty)
            QrImageView(
              data: widget.qrUri,
              size: 180,
              backgroundColor: Colors.white,
            )
          else
            const Text('QR no disponible'),
          const SizedBox(height: 16),
          TextField(
            controller: _codeController,
            keyboardType: TextInputType.number,
            maxLength: 6,
            decoration: const InputDecoration(
              labelText: 'Código de verificación',
              counterText: '',
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancelar'),
        ),
        TextButton(
          onPressed: _loading ? null : _confirm,
          child: _loading
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Confirmar'),
        ),
      ],
    );
  }

  Future<void> _confirm() async {
    if (_codeController.text.length != 6) return;
    setState(() => _loading = true);
    try {
      await widget.ref
          .read(perfilRepositoryProvider)
          .confirmMfa(_codeController.text);
      if (mounted) Navigator.pop(context);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }
}
