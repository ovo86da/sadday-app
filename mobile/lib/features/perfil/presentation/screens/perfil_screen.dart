import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../../core/api/app_exception.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_avatar.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_dialog.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../../core/widgets/app_input.dart';
import '../../../docs_legales/domain/models/docs_legales_models.dart';
import '../../../docs_legales/presentation/providers/docs_legales_provider.dart';
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
    _tabController = TabController(length: 4, vsync: this);
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
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          tabs: const [
            Tab(text: 'Datos'),
            Tab(text: 'Contactos'),
            Tab(text: 'Salud'),
            Tab(text: 'Seguridad'),
          ],
        ),
      ),
      body: asyncPerfil.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => AppEmptyState(
          message: 'Error al cargar el perfil',
          error: e,
          actionLabel: 'Reintentar',
          onAction: () => ref.invalidate(perfilNotifierProvider),
        ),
        data: (perfil) => TabBarView(
          controller: _tabController,
          children: [
            _DatosTab(perfil: perfil),
            const _ContactosTab(),
            const _SaludTab(),
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
              style: TextButton.styleFrom(
                  foregroundColor: AppColors.destructive),
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Cerrar sesión')),
        ],
      ),
    );
    if (confirm == true) {
      await ref.read(authNotifierProvider.notifier).logout();
    }
  }
}

// ── Datos tab ─────────────────────────────────────────────────────────────────

class _DatosTab extends ConsumerWidget {
  const _DatosTab({required this.perfil});
  final PerfilSocio perfil;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
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
                    style: AppTextStyles.bodyMedium
                        .copyWith(color: AppColors.mutedFg)),
              if (perfil.nivelTecnico != null) ...[
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 12, vertical: 5),
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
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('Datos personales',
                style: AppTextStyles.titleMedium
                    .copyWith(fontWeight: FontWeight.w600)),
            TextButton.icon(
              icon: const Icon(Icons.edit_outlined, size: 15),
              label: const Text('Editar'),
              style: TextButton.styleFrom(
                  foregroundColor: AppColors.primary),
              onPressed: () => showModalBottomSheet<void>(
                context: context,
                isScrollControlled: true,
                backgroundColor: AppColors.background,
                shape: const RoundedRectangleBorder(
                  borderRadius:
                      BorderRadius.vertical(top: Radius.circular(16)),
                ),
                builder: (_) => _EditPerfilSheet(perfil: perfil),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        AppCard(
          child: Column(
            children: [
              if (perfil.cedula != null)
                _ReadonlyField('Cédula', perfil.cedula!),
              _ReadonlyField('Correo', perfil.correo ?? '—'),
              if (perfil.telefono != null)
                _ReadonlyField('Teléfono', perfil.telefono!),
              if (perfil.direccion != null)
                _ReadonlyField('Dirección', perfil.direccion!),
              if (perfil.tipoSocio != null)
                _ReadonlyField('Tipo de socio', perfil.tipoSocio!),
              if (perfil.estadoHabilitacion != null)
                _ReadonlyField('Estado', perfil.estadoHabilitacion!),
            ],
          ),
        ),
      ],
    );
  }
}

// ── Contactos tab ─────────────────────────────────────────────────────────────

const _relacionOptions = [
  'Cónyuge/Pareja', 'Madre', 'Padre', 'Hijo/a', 'Hermano/a',
  'Abuelo/a', 'Tío/a', 'Primo/a', 'Amigo/a', 'Otro',
];

class _ContactosTab extends ConsumerStatefulWidget {
  const _ContactosTab();

  @override
  ConsumerState<_ContactosTab> createState() => _ContactosTabState();
}

class _ContactosTabState extends ConsumerState<_ContactosTab> {
  bool _editing = false;
  bool _saving = false;
  String? _error;

  late TextEditingController _c1Nombre;
  late TextEditingController _c1Celular;
  late TextEditingController _c1Direccion;
  String? _c1Relacion;
  late TextEditingController _c2Nombre;
  late TextEditingController _c2Celular;
  late TextEditingController _c2Direccion;
  String? _c2Relacion;
  bool _hasSecond = false;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    _c1Nombre = TextEditingController();
    _c1Celular = TextEditingController();
    _c1Direccion = TextEditingController();
    _c2Nombre = TextEditingController();
    _c2Celular = TextEditingController();
    _c2Direccion = TextEditingController();
  }

  @override
  void dispose() {
    for (final c in [
      _c1Nombre, _c1Celular, _c1Direccion,
      _c2Nombre, _c2Celular, _c2Direccion
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  void _initFromData(List<EmergencyContact> contacts) {
    if (_initialized) return;
    _initialized = true;
    final c1 = contacts.isNotEmpty ? contacts[0] : null;
    final c2 = contacts.length > 1 ? contacts[1] : null;
    _c1Nombre.text = c1?.nombreCompleto ?? '';
    _c1Celular.text = c1?.celular ?? '';
    _c1Direccion.text = c1?.direccion ?? '';
    _c1Relacion = c1?.relacion;
    _c2Nombre.text = c2?.nombreCompleto ?? '';
    _c2Celular.text = c2?.celular ?? '';
    _c2Direccion.text = c2?.direccion ?? '';
    _c2Relacion = c2?.relacion;
    _hasSecond = c2 != null;
  }

  Future<void> _save() async {
    if (_c1Nombre.text.trim().isEmpty || _c1Relacion == null) {
      setState(() => _error = 'El contacto 1 requiere nombre y relación');
      return;
    }
    if (_hasSecond &&
        (_c2Nombre.text.trim().isEmpty || _c2Relacion == null)) {
      setState(() => _error = 'El contacto 2 requiere nombre y relación');
      return;
    }
    setState(() { _saving = true; _error = null; });
    try {
      final contactos = [
        {
          'orden': 1,
          'nombreCompleto': _c1Nombre.text.trim(),
          'relacion': _c1Relacion!,
          if (_c1Celular.text.isNotEmpty) 'celular': _c1Celular.text.trim(),
          if (_c1Direccion.text.isNotEmpty)
            'direccion': _c1Direccion.text.trim(),
        },
        if (_hasSecond)
          {
            'orden': 2,
            'nombreCompleto': _c2Nombre.text.trim(),
            'relacion': _c2Relacion!,
            if (_c2Celular.text.isNotEmpty)
              'celular': _c2Celular.text.trim(),
            if (_c2Direccion.text.isNotEmpty)
              'direccion': _c2Direccion.text.trim(),
          },
      ];
      await ref
          .read(docsLegalesRepositoryProvider)
          .upsertContactos(contactos);
      ref.invalidate(misContactosProvider);
      ref.invalidate(profileStatusProvider);
      setState(() { _editing = false; _initialized = false; });
    } catch (e) {
      setState(() => _error = unwrapDio(e).toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(misContactosProvider);

    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
        message: 'Error al cargar contactos',
        error: e,
        actionLabel: 'Reintentar',
        onAction: () => ref.invalidate(misContactosProvider),
      ),
      data: (contacts) {
        _initFromData(contacts);
        if (!_editing) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Contactos de emergencia',
                      style: AppTextStyles.titleMedium
                          .copyWith(fontWeight: FontWeight.w600)),
                  TextButton.icon(
                    icon: const Icon(Icons.edit_outlined, size: 15),
                    label: const Text('Editar'),
                    style: TextButton.styleFrom(
                        foregroundColor: AppColors.primary),
                    onPressed: () => setState(() => _editing = true),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              if (contacts.isEmpty)
                _EmptyContactsCard()
              else
                ...contacts.map((c) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: AppCard(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(children: [
                              const Icon(Icons.contact_phone_outlined,
                                  size: 18, color: AppColors.primary),
                              const SizedBox(width: 8),
                              Text(c.nombreCompleto,
                                  style: AppTextStyles.bodyMedium.copyWith(
                                      fontWeight: FontWeight.w600)),
                            ]),
                            const SizedBox(height: 6),
                            _ReadonlyField('Relación', c.relacion),
                            if (c.celular.isNotEmpty)
                              _ReadonlyField('Celular', c.celular),
                            if (c.direccion != null)
                              _ReadonlyField('Dirección', c.direccion!),
                          ],
                        ),
                      ),
                    )),
            ],
          );
        }

        // Edit mode
        return SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Editar contactos',
                      style: AppTextStyles.titleMedium
                          .copyWith(fontWeight: FontWeight.w600)),
                  TextButton(
                    onPressed: () => setState(() {
                      _editing = false;
                      _error = null;
                    }),
                    child: const Text('Cancelar'),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _contactFields('Contacto 1', _c1Nombre, _c1Celular,
                  _c1Direccion, _c1Relacion,
                  (v) => setState(() => _c1Relacion = v)),
              const SizedBox(height: 20),
              if (_hasSecond)
                _contactFields('Contacto 2', _c2Nombre, _c2Celular,
                    _c2Direccion, _c2Relacion,
                    (v) => setState(() => _c2Relacion = v))
              else
                TextButton.icon(
                  icon: const Icon(Icons.add),
                  label: const Text('Agregar segundo contacto'),
                  onPressed: () => setState(() => _hasSecond = true),
                ),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!,
                    style: const TextStyle(color: AppColors.destructive)),
              ],
              const SizedBox(height: 24),
              AppButton(
                label: 'Guardar contactos',
                loading: _saving,
                onPressed: _save,
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _contactFields(
    String title,
    TextEditingController nombre,
    TextEditingController celular,
    TextEditingController direccion,
    String? relacion,
    void Function(String?) onRelacionChanged,
  ) =>
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title,
              style: AppTextStyles.bodyMedium
                  .copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          AppInput(label: 'Nombre completo *', controller: nombre),
          const SizedBox(height: 8),
          _RelacionSelector(value: relacion, onChanged: onRelacionChanged),
          const SizedBox(height: 8),
          AppInput(
              label: 'Celular',
              controller: celular,
              keyboardType: TextInputType.phone),
          const SizedBox(height: 8),
          AppInput(label: 'Dirección', controller: direccion),
        ],
      );
}

class _EmptyContactsCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.destructive.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
              color: AppColors.destructive.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            const Icon(Icons.warning_amber_rounded,
                color: AppColors.destructive, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'No tienes contactos de emergencia registrados. Son obligatorios para inscribirte en salidas.',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.destructive),
              ),
            ),
          ],
        ),
      );
}

class _RelacionSelector extends StatelessWidget {
  const _RelacionSelector(
      {required this.value, required this.onChanged});
  final String? value;
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
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.border,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text('Relación', style: AppTextStyles.titleMedium),
                const SizedBox(height: 8),
                ..._relacionOptions.map((o) => ListTile(
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
                    Text('Relación *',
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

// ── Salud tab ─────────────────────────────────────────────────────────────────

const _bloodTypes = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

class _SaludTab extends ConsumerStatefulWidget {
  const _SaludTab();

  @override
  ConsumerState<_SaludTab> createState() => _SaludTabState();
}

class _SaludTabState extends ConsumerState<_SaludTab> {
  bool _editing = false;
  bool _saving = false;
  String? _error;

  String? _bloodType;
  bool _alergias = false;
  late TextEditingController _alergiasDetail;
  bool _condicion = false;
  late TextEditingController _condicionDetail;
  bool _medicacion = false;
  late TextEditingController _medicacionDetail;
  late TextEditingController _notas;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    _alergiasDetail = TextEditingController();
    _condicionDetail = TextEditingController();
    _medicacionDetail = TextEditingController();
    _notas = TextEditingController();
  }

  @override
  void dispose() {
    _alergiasDetail.dispose();
    _condicionDetail.dispose();
    _medicacionDetail.dispose();
    _notas.dispose();
    super.dispose();
  }

  void _initFromData(MedicalInfo? info) {
    if (_initialized) return;
    _initialized = true;
    if (info == null) return;
    _bloodType = info.bloodType;
    _alergias = info.hasRelevantAllergies;
    _alergiasDetail.text = info.allergiesDetail ?? '';
    _condicion = info.hasRelevantMedicalCondition;
    _condicionDetail.text = info.medicalConditionDetail ?? '';
    _medicacion = info.usesEmergencyMedication;
    _medicacionDetail.text = info.emergencyMedicationDetail ?? '';
    _notas.text = info.additionalNotes ?? '';
  }

  Future<void> _save() async {
    setState(() { _saving = true; _error = null; });
    try {
      final data = MedicalInfo(
        bloodType: _bloodType,
        hasRelevantAllergies: _alergias,
        allergiesDetail: _alergias && _alergiasDetail.text.isNotEmpty
            ? _alergiasDetail.text
            : null,
        hasRelevantMedicalCondition: _condicion,
        medicalConditionDetail:
            _condicion && _condicionDetail.text.isNotEmpty
                ? _condicionDetail.text
                : null,
        usesEmergencyMedication: _medicacion,
        emergencyMedicationDetail:
            _medicacion && _medicacionDetail.text.isNotEmpty
                ? _medicacionDetail.text
                : null,
        additionalNotes:
            _notas.text.isNotEmpty ? _notas.text : null,
      ).toJson();
      await ref
          .read(docsLegalesRepositoryProvider)
          .updateInfoMedica(data);
      ref.invalidate(miInfoMedicaProvider);
      ref.invalidate(profileStatusProvider);
      setState(() { _editing = false; _initialized = false; });
    } catch (e) {
      setState(() => _error = unwrapDio(e).toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(miInfoMedicaProvider);

    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
        message: 'Error al cargar información médica',
        error: e,
        actionLabel: 'Reintentar',
        onAction: () => ref.invalidate(miInfoMedicaProvider),
      ),
      data: (info) {
        _initFromData(info);
        if (!_editing) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.primary.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                      color: AppColors.primary.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.lock_outline,
                        size: 16, color: AppColors.primary),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Datos confidenciales. Solo se comparten en emergencias.',
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.primary),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Información médica',
                      style: AppTextStyles.titleMedium
                          .copyWith(fontWeight: FontWeight.w600)),
                  TextButton.icon(
                    icon: const Icon(Icons.edit_outlined, size: 15),
                    label: const Text('Editar'),
                    style: TextButton.styleFrom(
                        foregroundColor: AppColors.primary),
                    onPressed: () => setState(() => _editing = true),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              if (info == null)
                _EmptyMedicalCard()
              else
                AppCard(
                  child: Column(
                    children: [
                      _ReadonlyField(
                          'Tipo de sangre', info.bloodType ?? '—'),
                      _ReadonlyField(
                          'Alergias relevantes',
                          info.hasRelevantAllergies
                              ? (info.allergiesDetail ?? 'Sí')
                              : 'No'),
                      _ReadonlyField(
                          'Condición médica',
                          info.hasRelevantMedicalCondition
                              ? (info.medicalConditionDetail ?? 'Sí')
                              : 'No'),
                      _ReadonlyField(
                          'Medicación emergencia',
                          info.usesEmergencyMedication
                              ? (info.emergencyMedicationDetail ?? 'Sí')
                              : 'No'),
                      if (info.additionalNotes != null)
                        _ReadonlyField('Notas', info.additionalNotes!),
                    ],
                  ),
                ),
            ],
          );
        }

        // Edit mode
        return SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Editar información médica',
                      style: AppTextStyles.titleMedium
                          .copyWith(fontWeight: FontWeight.w600)),
                  TextButton(
                    onPressed: () =>
                        setState(() { _editing = false; _error = null; }),
                    child: const Text('Cancelar'),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _BloodTypeSelector(
                  value: _bloodType,
                  onChanged: (v) => setState(() => _bloodType = v)),
              const SizedBox(height: 12),
              _BoolField(
                  label: '¿Alergias relevantes?',
                  value: _alergias,
                  onChanged: (v) => setState(() => _alergias = v)),
              if (_alergias) ...[
                const SizedBox(height: 8),
                AppInput(
                    label: '¿Cuáles?',
                    controller: _alergiasDetail,
                    maxLines: 2),
              ],
              const SizedBox(height: 12),
              _BoolField(
                  label: '¿Condición médica relevante?',
                  value: _condicion,
                  onChanged: (v) => setState(() => _condicion = v)),
              if (_condicion) ...[
                const SizedBox(height: 8),
                AppInput(
                    label: '¿Cuál?',
                    controller: _condicionDetail,
                    maxLines: 2),
              ],
              const SizedBox(height: 12),
              _BoolField(
                  label: '¿Medicación de emergencia?',
                  value: _medicacion,
                  onChanged: (v) => setState(() => _medicacion = v)),
              if (_medicacion) ...[
                const SizedBox(height: 8),
                AppInput(
                    label: '¿Cuál?',
                    controller: _medicacionDetail,
                    maxLines: 2),
              ],
              const SizedBox(height: 12),
              AppInput(
                  label: 'Notas adicionales (opcional)',
                  controller: _notas,
                  maxLines: 3),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!,
                    style: const TextStyle(
                        color: AppColors.destructive)),
              ],
              const SizedBox(height: 24),
              AppButton(
                  label: 'Guardar',
                  loading: _saving,
                  onPressed: _save),
            ],
          ),
        );
      },
    );
  }
}

class _EmptyMedicalCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.destructive.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
              color: AppColors.destructive.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            const Icon(Icons.warning_amber_rounded,
                color: AppColors.destructive, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'No has completado tu información médica. Es obligatoria para inscribirte en salidas.',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.destructive),
              ),
            ),
          ],
        ),
      );
}

class _BloodTypeSelector extends StatelessWidget {
  const _BloodTypeSelector(
      {required this.value, required this.onChanged});
  final String? value;
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
                Text('Tipo de sangre', style: AppTextStyles.titleMedium),
                const SizedBox(height: 8),
                ..._bloodTypes.map((o) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(o),
                      trailing: o == value
                          ? const Icon(Icons.check,
                              color: AppColors.primary)
                          : null,
                      onTap: () => Navigator.pop(context, o),
                    )),
                if (value != null) ...[
                  const Divider(),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Borrar',
                        style:
                            TextStyle(color: AppColors.destructive)),
                    onTap: () => Navigator.pop(context, ''),
                  ),
                ],
              ],
            ),
          );
          if (sel == null) return;
          onChanged(sel.isEmpty ? null : sel);
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
                    Text('Tipo de sangre (opcional)',
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

class _BoolField extends StatelessWidget {
  const _BoolField(
      {required this.label,
      required this.value,
      required this.onChanged});
  final String label;
  final bool value;
  final void Function(bool) onChanged;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: AppColors.secondary,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          children: [
            Expanded(child: Text(label, style: AppTextStyles.bodyMedium)),
            Switch(
                value: value,
                activeThumbColor: AppColors.primary,
                onChanged: onChanged),
          ],
        ),
      );
}

// ── Seguridad tab ─────────────────────────────────────────────────────────────

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
        Text('Contraseña',
            style: AppTextStyles.titleMedium
                .copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        AppCard(
          child: ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.lock_outline,
                color: AppColors.mutedFg, size: 20),
            title: const Text('Cambiar contraseña'),
            trailing: const Icon(Icons.chevron_right,
                color: AppColors.mutedFg, size: 20),
            onTap: () => showModalBottomSheet<void>(
              context: context,
              isScrollControlled: true,
              backgroundColor: AppColors.background,
              shape: const RoundedRectangleBorder(
                borderRadius:
                    BorderRadius.vertical(top: Radius.circular(16)),
              ),
              builder: (_) => const _ChangePasswordSheet(),
            ),
          ),
        ),
        const SizedBox(height: 16),
        Text('Autenticación de dos factores',
            style: AppTextStyles.titleMedium
                .copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        AppCard(
          child: mfaAsync.when(
            loading: () => const CircularProgressIndicator(),
            error: (_, _) =>
                const Text('Error al cargar estado MFA'),
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
        Text('Biometría',
            style: AppTextStyles.titleMedium
                .copyWith(fontWeight: FontWeight.w600)),
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
        Text('Sesiones activas',
            style: AppTextStyles.titleMedium
                .copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        sesionesAsync.when(
          loading: () =>
              const Center(child: CircularProgressIndicator()),
          error: (e, _) => AppEmptyState(
              message: 'Error al cargar sesiones', error: e),
          data: (sesiones) => AppCard(
            child: Column(
              children: [
                ...sesiones.map((s) => _SesionItem(
                      sesion: s,
                      onCerrar: () async {
                        await ref
                            .read(perfilRepositoryProvider)
                            .cerrarSesion(s.id);
                        ref.invalidate(sesionesProvider);
                      },
                    )),
                if (sesiones.length > 1) ...[
                  const Divider(color: AppColors.border),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text(
                        'Cerrar todas las demás sesiones',
                        style: TextStyle(
                            color: AppColors.destructive,
                            fontSize: 14)),
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
      final setup =
          await ref.read(perfilRepositoryProvider).setupMfa();
      if (!context.mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: AppColors.background,
        shape: const RoundedRectangleBorder(
          borderRadius:
              BorderRadius.vertical(top: Radius.circular(16)),
        ),
        builder: (_) => _MfaSetupSheet(setup: setup, ref: ref),
      );
      ref.invalidate(mfaStatusProvider);
    } else {
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: AppColors.background,
        shape: const RoundedRectangleBorder(
          borderRadius:
              BorderRadius.vertical(top: Radius.circular(16)),
        ),
        builder: (_) => _MfaDisableSheet(ref: ref),
      );
      ref.invalidate(mfaStatusProvider);
    }
  }
}

// ── Shared widgets ────────────────────────────────────────────────────────────

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
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
          ),
          Expanded(
              child: Text(value, style: AppTextStyles.bodyMedium)),
        ]),
      );
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
              sesion.ultimaActividad!
                  .toLocal()
                  .toString()
                  .substring(0, 16),
              style: AppTextStyles.labelSmall
                  .copyWith(color: AppColors.mutedFg))
          : null,
      trailing: sesion.esCurrent
          ? const Text('Actual',
              style: TextStyle(
                  color: AppColors.salidaRealizada, fontSize: 12))
          : IconButton(
              icon: const Icon(Icons.close,
                  color: AppColors.destructive, size: 18),
              onPressed: onCerrar,
            ),
    );
  }
}

// ── Edit perfil sheet (solo correo/teléfono/dirección) ────────────────────────

class _EditPerfilSheet extends ConsumerStatefulWidget {
  const _EditPerfilSheet({required this.perfil});
  final PerfilSocio perfil;

  @override
  ConsumerState<_EditPerfilSheet> createState() =>
      _EditPerfilSheetState();
}

class _EditPerfilSheetState extends ConsumerState<_EditPerfilSheet> {
  late final TextEditingController _correo;
  late final TextEditingController _telefono;
  late final TextEditingController _direccion;
  bool _isDirty = false;
  bool _loading = false;
  String? _error;

  void _markDirty() => setState(() => _isDirty = true);

  @override
  void initState() {
    super.initState();
    final p = widget.perfil;
    _correo = TextEditingController(text: p.correo ?? '');
    _telefono = TextEditingController(text: p.telefono ?? '');
    _direccion = TextEditingController(text: p.direccion ?? '');
    for (final c in [_correo, _telefono, _direccion]) {
      c.addListener(_markDirty);
    }
  }

  @override
  void dispose() {
    for (final c in [_correo, _telefono, _direccion]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _tryClose() async {
    if (!_isDirty) { Navigator.of(context).pop(); return; }
    final discard = await showAppDialog(
      context: context,
      title: 'Descartar cambios',
      message: '¿Salir sin guardar los cambios?',
      confirmLabel: 'Descartar',
      confirmVariant: AppButtonVariant.destructive,
    );
    if ((discard ?? false) && mounted) Navigator.of(context).pop();
  }

  Future<void> _save() async {
    setState(() { _loading = true; _error = null; });
    try {
      await ref.read(perfilNotifierProvider.notifier).actualizar({
        'correo': _correo.text,
        'telefono': _telefono.text,
        'direccion': _direccion.text,
      });
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      setState(() => _error = unwrapDio(e).toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_isDirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (!didPop) await _tryClose();
      },
      child: Padding(
        padding: EdgeInsets.only(
            bottom: MediaQuery.of(context).viewInsets.bottom),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 36,
                  height: 4,
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
                  Text('Editar perfil',
                      style: AppTextStyles.titleMedium),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20),
                    onPressed: _tryClose,
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(),
                    color: AppColors.mutedFg,
                  ),
                ],
              ),
              const SizedBox(height: 20),
              AppInput(
                  label: 'Correo',
                  controller: _correo,
                  keyboardType: TextInputType.emailAddress),
              const SizedBox(height: 12),
              AppInput(
                  label: 'Teléfono',
                  controller: _telefono,
                  keyboardType: TextInputType.phone),
              const SizedBox(height: 12),
              AppInput(label: 'Dirección', controller: _direccion),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!,
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.destructive)),
              ],
              const SizedBox(height: 24),
              AppButton(
                  label: 'Guardar cambios',
                  loading: _loading,
                  onPressed: _save),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Change password sheet ─────────────────────────────────────────────────────

class _ChangePasswordSheet extends ConsumerStatefulWidget {
  const _ChangePasswordSheet();

  @override
  ConsumerState<_ChangePasswordSheet> createState() =>
      _ChangePasswordSheetState();
}

class _ChangePasswordSheetState extends ConsumerState<_ChangePasswordSheet> {
  final _actualCtrl    = TextEditingController();
  final _nuevaCtrl     = TextEditingController();
  final _confirmarCtrl = TextEditingController();
  bool _obscureActual    = true;
  bool _obscureNueva     = true;
  bool _obscureConfirmar = true;
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _actualCtrl.dispose();
    _nuevaCtrl.dispose();
    _confirmarCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final actual    = _actualCtrl.text;
    final nueva     = _nuevaCtrl.text;
    final confirmar = _confirmarCtrl.text;

    if (actual.isEmpty || nueva.isEmpty || confirmar.isEmpty) {
      setState(() => _error = 'Todos los campos son obligatorios');
      return;
    }
    if (nueva != confirmar) {
      setState(() => _error = 'Las contraseñas nuevas no coinciden');
      return;
    }
    if (nueva == actual) {
      setState(
          () => _error = 'La nueva contraseña debe ser diferente a la actual');
      return;
    }

    setState(() { _loading = true; _error = null; });
    try {
      await ref.read(perfilRepositoryProvider).changePassword(
        currentPassword: actual,
        newPassword: nueva,
      );
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Contraseña actualizada correctamente')));
      }
    } catch (e) {
      setState(() => _error = unwrapDio(e).toString());
    } finally {
      if (mounted) setState(() => _loading = false);
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
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                      color: AppColors.border,
                      borderRadius: BorderRadius.circular(2))),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Cambiar contraseña',
                    style: AppTextStyles.titleMedium),
                IconButton(
                  icon: const Icon(Icons.close, size: 20),
                  onPressed: () => Navigator.of(context).pop(),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  color: AppColors.mutedFg,
                ),
              ],
            ),
            const SizedBox(height: 20),
            AppInput(
              label: 'Contraseña actual',
              controller: _actualCtrl,
              obscureText: _obscureActual,
              suffixIcon: IconButton(
                icon: Icon(
                    _obscureActual
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                    size: 18,
                    color: AppColors.mutedFg),
                onPressed: () =>
                    setState(() => _obscureActual = !_obscureActual),
              ),
            ),
            const SizedBox(height: 12),
            AppInput(
              label: 'Nueva contraseña',
              controller: _nuevaCtrl,
              obscureText: _obscureNueva,
              suffixIcon: IconButton(
                icon: Icon(
                    _obscureNueva
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                    size: 18,
                    color: AppColors.mutedFg),
                onPressed: () =>
                    setState(() => _obscureNueva = !_obscureNueva),
              ),
            ),
            const SizedBox(height: 12),
            AppInput(
              label: 'Confirmar nueva contraseña',
              controller: _confirmarCtrl,
              obscureText: _obscureConfirmar,
              suffixIcon: IconButton(
                icon: Icon(
                    _obscureConfirmar
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                    size: 18,
                    color: AppColors.mutedFg),
                onPressed: () =>
                    setState(() => _obscureConfirmar = !_obscureConfirmar),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!,
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.destructive)),
            ],
            const SizedBox(height: 24),
            AppButton(
                label: 'Actualizar contraseña',
                loading: _loading,
                onPressed: _save),
          ],
        ),
      ),
    );
  }
}

// ── MFA sheets (sin cambios respecto al original) ─────────────────────────────

class _MfaSetupSheet extends StatefulWidget {
  const _MfaSetupSheet({required this.setup, required this.ref});
  final ({String otpAuthUri, String base32Secret}) setup;
  final WidgetRef ref;

  @override
  State<_MfaSetupSheet> createState() => _MfaSetupSheetState();
}

class _MfaSetupSheetState extends State<_MfaSetupSheet> {
  final _codeCtrl = TextEditingController();
  bool _loading = false;
  bool _showQr = false;
  String? _error;

  @override
  void dispose() { _codeCtrl.dispose(); super.dispose(); }

  Future<void> _openInAuthApp() async {
    final uri = Uri.parse(widget.setup.otpAuthUri);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } else if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Instala Google Authenticator o Authy primero')));
    }
  }

  void _copySecret() {
    Clipboard.setData(ClipboardData(text: widget.setup.base32Secret));
    ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Código copiado al portapapeles')));
  }

  Future<void> _confirm() async {
    if (_codeCtrl.text.length != 6) return;
    setState(() { _loading = true; _error = null; });
    try {
      await widget.ref.read(perfilRepositoryProvider).confirmMfa(_codeCtrl.text);
      if (mounted) Navigator.of(context).pop();
    } catch (_) {
      setState(() => _error = 'Código incorrecto. Revisa tu app.');
    } finally {
      if (mounted) setState(() => _loading = false);
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
            Center(child: Container(width: 36, height: 4,
                decoration: BoxDecoration(color: AppColors.border,
                    borderRadius: BorderRadius.circular(2)))),
            const SizedBox(height: 16),
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Text('Activar segundo factor', style: AppTextStyles.titleMedium),
              IconButton(icon: const Icon(Icons.close, size: 20),
                  onPressed: () => Navigator.of(context).pop(),
                  padding: EdgeInsets.zero, constraints: const BoxConstraints(),
                  color: AppColors.mutedFg),
            ]),
            const SizedBox(height: 8),
            Text('Necesitas una app autenticadora como Google Authenticator o Authy.',
                style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
            const SizedBox(height: 20),
            Text('1. Abre en tu app autenticadora',
                style: AppTextStyles.bodySmall.copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            AppButton(label: 'Abrir en app de autenticación', onPressed: _openInAuthApp),
            const SizedBox(height: 16),
            Text('2. ¿No se abre? Copia el código manual',
                style: AppTextStyles.bodySmall.copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(color: AppColors.secondary,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.border)),
              child: Row(children: [
                Expanded(child: Text(widget.setup.base32Secret,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 13,
                        color: AppColors.foreground, letterSpacing: 1.2))),
                IconButton(icon: const Icon(Icons.copy, size: 18),
                    onPressed: _copySecret, padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(), color: AppColors.primary),
              ]),
            ),
            const SizedBox(height: 16),
            GestureDetector(
              onTap: () => setState(() => _showQr = !_showQr),
              child: Row(children: [
                Icon(_showQr ? Icons.expand_less : Icons.expand_more,
                    size: 18, color: AppColors.mutedFg),
                const SizedBox(width: 4),
                Text(_showQr ? 'Ocultar QR' : 'Ver QR (para activar desde otro dispositivo)',
                    style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
              ]),
            ),
            if (_showQr && widget.setup.otpAuthUri.isNotEmpty) ...[
              const SizedBox(height: 12),
              Center(child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(color: Colors.white,
                      borderRadius: BorderRadius.circular(8)),
                  child: QrImageView(data: widget.setup.otpAuthUri,
                      size: 160, backgroundColor: Colors.white))),
            ],
            const SizedBox(height: 20),
            Text('3. Ingresa el código de 6 dígitos',
                style: AppTextStyles.bodySmall.copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(controller: _codeCtrl, keyboardType: TextInputType.number,
                maxLength: 6, textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700,
                    letterSpacing: 8, color: AppColors.foreground),
                decoration: const InputDecoration(counterText: '', hintText: '------'),
                onChanged: (_) => setState(() => _error = null)),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!, style: AppTextStyles.bodySmall
                  .copyWith(color: AppColors.destructive)),
            ],
            const SizedBox(height: 20),
            AppButton(label: 'Confirmar y activar', loading: _loading, onPressed: _confirm),
          ],
        ),
      ),
    );
  }
}

class _MfaDisableSheet extends StatefulWidget {
  const _MfaDisableSheet({required this.ref});
  final WidgetRef ref;

  @override
  State<_MfaDisableSheet> createState() => _MfaDisableSheetState();
}

class _MfaDisableSheetState extends State<_MfaDisableSheet> {
  final _codeCtrl = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() { _codeCtrl.dispose(); super.dispose(); }

  Future<void> _disable() async {
    if (_codeCtrl.text.length != 6) return;
    setState(() { _loading = true; _error = null; });
    try {
      await widget.ref.read(perfilRepositoryProvider).disableMfa(_codeCtrl.text);
      if (mounted) Navigator.of(context).pop();
    } catch (_) {
      setState(() => _error = 'Código incorrecto. Revisa tu app.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min, children: [
          Center(child: Container(width: 36, height: 4,
              decoration: BoxDecoration(color: AppColors.border,
                  borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 16),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            Text('Desactivar 2FA', style: AppTextStyles.titleMedium),
            IconButton(icon: const Icon(Icons.close, size: 20),
                onPressed: () => Navigator.of(context).pop(),
                padding: EdgeInsets.zero, constraints: const BoxConstraints(),
                color: AppColors.mutedFg),
          ]),
          const SizedBox(height: 8),
          Text('Ingresa el código de 6 dígitos de tu app para confirmar.',
              style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
          const SizedBox(height: 20),
          TextField(controller: _codeCtrl, keyboardType: TextInputType.number,
              maxLength: 6, textAlign: TextAlign.center, autofocus: true,
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700,
                  letterSpacing: 8, color: AppColors.foreground),
              decoration: const InputDecoration(counterText: '', hintText: '------'),
              onChanged: (_) => setState(() => _error = null)),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: AppTextStyles.bodySmall
                .copyWith(color: AppColors.destructive)),
          ],
          const SizedBox(height: 20),
          AppButton(label: 'Desactivar 2FA', variant: AppButtonVariant.destructive,
              loading: _loading, onPressed: _disable),
        ]),
      ),
    );
  }
}
