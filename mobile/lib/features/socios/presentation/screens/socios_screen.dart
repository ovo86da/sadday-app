import 'dart:io';

import 'package:flutter/material.dart';
import '../../../../core/api/app_exception.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_avatar.dart';
import '../../../../core/widgets/app_badge.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../../core/widgets/app_dialog.dart';
import '../../../../core/widgets/app_input.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../domain/models/socio_model.dart';
import '../providers/socios_provider.dart';

class SociosScreen extends ConsumerStatefulWidget {
  const SociosScreen({super.key});

  @override
  ConsumerState<SociosScreen> createState() => _SociosScreenState();
}

class _SociosScreenState extends ConsumerState<SociosScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tab;
  final _search = TextEditingController();
  int? _filterRolId;
  int? _filterEstadoId;
  int _listKey = 0;
  // /v1/socios/invitaciones está restringido a ADMIN/SECRETARIA en el backend.
  late final bool _canSeeInvitaciones;

  @override
  void initState() {
    super.initState();
    final auth = ref.read(authNotifierProvider).asData?.value;
    final rol = auth is AuthAuthenticated ? auth.user.rol : null;
    _canSeeInvitaciones =
        rol == UserRole.admin || rol == UserRole.secretaria;
    _tab = TabController(length: _canSeeInvitaciones ? 2 : 1, vsync: this);
  }

  @override
  void dispose() {
    _tab.dispose();
    _search.dispose();
    super.dispose();
  }

  void _applySearch() => setState(() => _listKey++);

  UserRole? get _userRole {
    final auth = ref.read(authNotifierProvider).asData?.value;
    return auth is AuthAuthenticated ? auth.user.rol : null;
  }

  bool get _canCreate =>
      _userRole == UserRole.admin || _userRole == UserRole.secretaria;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Socios'),
        centerTitle: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.file_download_outlined),
            tooltip: 'Exportar',
            onPressed: () => _showExportSheet(context),
          ),
        ],
        bottom: TabBar(
          controller: _tab,
          tabs: [
            const Tab(text: 'Lista'),
            if (_canSeeInvitaciones) const Tab(text: 'Invitaciones'),
          ],
        ),
      ),
      floatingActionButton: _canCreate
          ? FloatingActionButton(
              onPressed: () => _showSocioForm(context),
              child: const Icon(Icons.person_add_outlined),
            )
          : null,
      body: TabBarView(
        controller: _tab,
        children: [
          _ListaTab(
            listKey: _listKey,
            search: _search,
            filterRolId: _filterRolId,
            filterEstadoId: _filterEstadoId,
            onApplySearch: _applySearch,
            onFilterRol: (v) => setState(() {
              _filterRolId = v;
              _listKey++;
            }),
            onFilterEstado: (v) => setState(() {
              _filterEstadoId = v;
              _listKey++;
            }),
          ),
          if (_canSeeInvitaciones) const _InvitacionesTab(),
        ],
      ),
    );
  }

  void _showSocioForm(BuildContext context, [Socio? socio]) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => SocioFormSheet(
        socio: socio,
        onSaved: () {
          setState(() => _listKey++);
          ref.invalidate(invitacionesProvider);
        },
      ),
    );
  }

  void _showExportSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => const _ExportSociosSheet(),
    );
  }
}

// ── Bottom sheet — Exportar socios ─────────────────────────────────────────

enum _ExportFormat { csv, pdf, firmas }

class _ExportField {
  const _ExportField(this.key, this.label, this.defaultOn);
  final String key;
  final String label;
  final bool defaultOn;
}

// Las claves deben coincidir con las que acepta el backend (SocioExportController).
const _kExportFields = <_ExportField>[
  _ExportField('apellido', 'Apellido', true),
  _ExportField('nombre', 'Nombre', true),
  _ExportField('cedula', 'Cédula', true),
  _ExportField('correo', 'Correo electrónico', true),
  _ExportField('telefono', 'Teléfono', false),
  _ExportField('fechaNacimiento', 'Fecha de nacimiento', false),
  _ExportField('edad', 'Edad (años)', false),
  _ExportField('fechaIngreso', 'Fecha de ingreso', false),
  _ExportField('antiguedadAnios', 'Antigüedad (años)', false),
  _ExportField('fechaSalida', 'Fecha de salida', false),
  _ExportField('direccion', 'Dirección', false),
  _ExportField('tipoSangre', 'Tipo de sangre', false),
  _ExportField('tipoSocio', 'Tipo de socio', true),
  _ExportField('nivelTecnico', 'Nivel técnico', false),
  _ExportField('estadoHabilitacion', 'Estado habilitación', true),
  _ExportField('estadoAcceso', 'Estado de acceso', false),
  _ExportField('emergencyContactName', 'Contacto emergencia 1 — nombre', false),
  _ExportField('emergencyContactPhone', 'Contacto emergencia 1 — teléfono', false),
  _ExportField('emergencyContactName2', 'Contacto emergencia 2 — nombre', false),
  _ExportField('emergencyContactPhone2', 'Contacto emergencia 2 — teléfono', false),
];

class _ExportSociosSheet extends ConsumerStatefulWidget {
  const _ExportSociosSheet();

  @override
  ConsumerState<_ExportSociosSheet> createState() => _ExportSociosSheetState();
}

class _ExportSociosSheetState extends ConsumerState<_ExportSociosSheet> {
  static const _pdfMaxFields = 6;

  _ExportFormat _format = _ExportFormat.csv;
  final Set<String> _selected = {
    for (final f in _kExportFields)
      if (f.defaultOn) f.key,
  };
  bool _excludeAdmin = true;
  bool _loading = false;
  String? _error;

  // Campos seleccionados respetando el orden canónico de _kExportFields.
  List<String> get _orderedFields => [
        for (final f in _kExportFields)
          if (_selected.contains(f.key)) f.key,
      ];

  bool get _showFields => _format != _ExportFormat.firmas;

  bool get _pdfOverflow =>
      _format == _ExportFormat.pdf && _selected.length > _pdfMaxFields;

  Future<void> _export() async {
    final fields = _orderedFields;
    if (_showFields && fields.isEmpty) {
      setState(() => _error = 'Selecciona al menos un campo a exportar.');
      return;
    }
    if (_pdfOverflow) {
      setState(() => _error =
          'El PDF admite máximo $_pdfMaxFields columnas. Deselecciona ${_selected.length - _pdfMaxFields}.');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final repo = ref.read(sociosRepositoryProvider);
      final fecha = DateFormat('yyyy-MM-dd').format(DateTime.now());
      final List<int> bytes;
      final String filename;
      final String mime;

      switch (_format) {
        case _ExportFormat.csv:
          bytes = await repo.exportarCsv(
              fields: fields, excludeAdmin: _excludeAdmin);
          filename = 'socios-$fecha.csv';
          mime = 'text/csv';
        case _ExportFormat.pdf:
          bytes = await repo.exportarPdf(
              fields: fields, excludeAdmin: _excludeAdmin);
          filename = 'socios-$fecha.pdf';
          mime = 'application/pdf';
        case _ExportFormat.firmas:
          bytes = await repo.exportarPdf(
              firmas: true, excludeAdmin: _excludeAdmin);
          filename = 'socios-firmas-$fecha.pdf';
          mime = 'application/pdf';
      }

      if (bytes.isEmpty) {
        throw Exception('El archivo generado llegó vacío.');
      }

      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/$filename');
      await file.writeAsBytes(bytes);

      await SharePlus.instance.share(
        ShareParams(files: [XFile(file.path, mimeType: mime, name: filename)]),
      );

      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = 'No se pudo exportar: $e';
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.88,
        ),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
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
              Text('Exportar socios', style: AppTextStyles.titleMedium),
              const SizedBox(height: 16),

              // Formato
              Text('Formato',
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg)),
              const SizedBox(height: 8),
              Row(
                children: [
                  _formatOption(
                      _ExportFormat.csv, Icons.table_chart_outlined, 'Lista CSV'),
                  const SizedBox(width: 8),
                  _formatOption(_ExportFormat.pdf,
                      Icons.picture_as_pdf_outlined, 'Lista PDF'),
                  const SizedBox(width: 8),
                  _formatOption(_ExportFormat.firmas,
                      Icons.assignment_outlined, 'Hoja firmas'),
                ],
              ),
              const SizedBox(height: 20),

              // Campos / nota de firmas
              if (_showFields) ...[
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Campos a exportar',
                        style: AppTextStyles.bodyMedium
                            .copyWith(fontWeight: FontWeight.w600)),
                    Text(
                      _format == _ExportFormat.pdf
                          ? '${_selected.length} / $_pdfMaxFields'
                          : '${_selected.length} seleccionados',
                      style: AppTextStyles.bodySmall.copyWith(
                        color: _pdfOverflow
                            ? AppColors.destructive
                            : AppColors.mutedFg,
                      ),
                    ),
                  ],
                ),
                if (_format == _ExportFormat.pdf)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(
                        'El PDF admite máximo $_pdfMaxFields columnas.',
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg)),
                  ),
                const SizedBox(height: 8),
                Container(
                  decoration: BoxDecoration(
                    border: Border.all(color: AppColors.border),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Column(
                    children: [
                      for (var i = 0; i < _kExportFields.length; i++) ...[
                        if (i > 0) const Divider(height: 1),
                        _fieldTile(_kExportFields[i]),
                      ],
                    ],
                  ),
                ),
              ] else
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.secondary,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Columnas de la hoja',
                          style: AppTextStyles.bodyMedium
                              .copyWith(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 4),
                      Text(
                        'Cédula · Apellido · Nombre · Firma\n'
                        'Formato fijo — filas para firma manuscrita.',
                        style: AppTextStyles.bodySmall.copyWith(
                            color: AppColors.mutedFg, height: 1.4),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 8),

              // Excluir admin
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                controlAffinity: ListTileControlAffinity.leading,
                dense: true,
                title: Text('Excluir cuentas Admin',
                    style: AppTextStyles.bodyMedium),
                value: _excludeAdmin,
                onChanged: _loading
                    ? null
                    : (v) => setState(() => _excludeAdmin = v ?? true),
              ),

              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!,
                    style: const TextStyle(
                        color: AppColors.destructive, fontSize: 13)),
              ],
              const SizedBox(height: 16),
              AppButton(
                label: 'Descargar y compartir',
                fullWidth: true,
                loading: _loading,
                onPressed: _pdfOverflow ? null : _export,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _formatOption(_ExportFormat fmt, IconData icon, String label) {
    final selected = _format == fmt;
    return Expanded(
      child: GestureDetector(
        onTap: _loading
            ? null
            : () => setState(() {
                  _format = fmt;
                  _error = null;
                }),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 6),
          decoration: BoxDecoration(
            color: selected
                ? AppColors.primary.withValues(alpha: 0.12)
                : AppColors.secondary,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
                color: selected ? AppColors.primary : AppColors.border),
          ),
          child: Column(
            children: [
              Icon(icon,
                  size: 20,
                  color: selected ? AppColors.primary : AppColors.mutedFg),
              const SizedBox(height: 4),
              Text(
                label,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight:
                      selected ? FontWeight.w600 : FontWeight.w400,
                  color: selected ? AppColors.primary : AppColors.mutedFg,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _fieldTile(_ExportField field) {
    final checked = _selected.contains(field.key);
    return CheckboxListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 12),
      controlAffinity: ListTileControlAffinity.leading,
      dense: true,
      title: Text(field.label, style: AppTextStyles.bodyMedium),
      value: checked,
      onChanged: _loading
          ? null
          : (v) => setState(() {
                if (v ?? false) {
                  _selected.add(field.key);
                } else {
                  _selected.remove(field.key);
                }
                _error = null;
              }),
    );
  }
}

class _ListaTab extends ConsumerWidget {
  const _ListaTab({
    required this.listKey,
    required this.search,
    required this.filterRolId,
    required this.filterEstadoId,
    required this.onApplySearch,
    required this.onFilterRol,
    required this.onFilterEstado,
  });

  final int listKey;
  final TextEditingController search;
  final int? filterRolId;
  final int? filterEstadoId;
  final VoidCallback onApplySearch;
  final ValueChanged<int?> onFilterRol;
  final ValueChanged<int?> onFilterEstado;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lookups = ref.watch(sociosLookupsProvider).asData?.value;
    final roles = lookups?.roles ?? const <SocioLookupItem>[];
    final estados = lookups?.estados ?? const <SocioLookupItem>[];

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: AppInput(
            controller: search,
            hint: 'Buscar por nombre o cédula…',
            prefixIcon: Icons.search,
            onSubmitted: (_) => onApplySearch(),
          ),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Row(
            children: [
              _FilterChip(
                label: 'Rol',
                items: roles,
                selectedId: filterRolId,
                onChanged: onFilterRol,
              ),
              const SizedBox(width: 8),
              _FilterChip(
                label: 'Estado',
                items: estados,
                selectedId: filterEstadoId,
                onChanged: onFilterEstado,
              ),
            ],
          ),
        ),
        Expanded(
          child: AppPagedList<Socio>(
            key: ValueKey('socios-$listKey'),
            loader: (p) => ref.read(sociosRepositoryProvider).getSocios(
                  page: p,
                  q: search.text.trim().isEmpty ? null : search.text.trim(),
                  rolId: filterRolId,
                  estadoId: filterEstadoId,
                ),
            emptyMessage: 'Sin socios',
            itemBuilder: (ctx, socio, _) => _SocioListItem(socio: socio),
          ),
        ),
      ],
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    required this.label,
    required this.items,
    required this.selectedId,
    required this.onChanged,
  });

  final String label;
  final List<SocioLookupItem> items;
  final int? selectedId;
  final ValueChanged<int?> onChanged;

  String? _nombreOf(int? id) {
    if (id == null) return null;
    for (final i in items) {
      if (i.id == id) return i.nombre;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final active = selectedId != null;
    final chipText = _nombreOf(selectedId) ?? label;
    return GestureDetector(
      onTap: items.isEmpty
          ? null
          : () => showModalBottomSheet(
                context: context,
                backgroundColor: AppColors.background,
                builder: (_) => Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ListTile(
                      title: Text('$label: Todos',
                          style: TextStyle(
                              color: selectedId == null
                                  ? AppColors.primary
                                  : AppColors.foreground)),
                      onTap: () {
                        Navigator.pop(context);
                        onChanged(null);
                      },
                    ),
                    ...items.map((o) => ListTile(
                          title: Text(o.nombre,
                              style: TextStyle(
                                  color: o.id == selectedId
                                      ? AppColors.primary
                                      : AppColors.foreground)),
                          trailing: o.id == selectedId
                              ? const Icon(Icons.check,
                                  color: AppColors.primary)
                              : null,
                          onTap: () {
                            Navigator.pop(context);
                            onChanged(o.id);
                          },
                        )),
                  ],
                ),
              ),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: active
              ? AppColors.primary.withValues(alpha: 0.15)
              : AppColors.secondary,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: active ? AppColors.primary : AppColors.border,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              chipText,
              style: TextStyle(
                color: active ? AppColors.primary : AppColors.mutedFg,
                fontSize: 12,
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(width: 4),
            Icon(Icons.arrow_drop_down,
                size: 16,
                color: active ? AppColors.primary : AppColors.mutedFg),
          ],
        ),
      ),
    );
  }
}

class _SocioListItem extends StatelessWidget {
  const _SocioListItem({required this.socio});
  final Socio socio;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      onTap: () => context.push('/socios/${socio.id}'),
      child: Row(
        children: [
          AppAvatar(name: socio.nombreCompleto, size: 42),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(socio.nombreCompleto,
                    style: AppTextStyles.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 2),
                Text(socio.correo,
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg),
                    overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              AppBadge(label: socio.rol, color: AppColors.primary),
              const SizedBox(height: 4),
              _estadoBadge(socio.estadoHabilitacion),
            ],
          ),
        ],
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
}

class _InvitacionesTab extends ConsumerWidget {
  const _InvitacionesTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(invitacionesProvider);
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
        message: 'Error al cargar invitaciones',
        error: e,
        actionLabel: 'Reintentar',
        onAction: () => ref.invalidate(invitacionesProvider),
      ),
      data: (items) => items.isEmpty
          ? const AppEmptyState(message: 'Sin invitaciones pendientes')
          : RefreshIndicator(
              onRefresh: () async => ref.invalidate(invitacionesProvider),
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: items.length,
                itemBuilder: (_, i) =>
                    _InvitacionItem(inv: items[i], ref: ref),
              ),
            ),
    );
  }
}

class _InvitacionItem extends StatelessWidget {
  const _InvitacionItem({required this.inv, required this.ref});
  final Invitacion inv;
  final WidgetRef ref;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd/MM/yyyy', 'es');
    return AppCard(
      child: Row(
        children: [
          AppAvatar(name: inv.nombreCompleto, size: 38),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(inv.nombreCompleto,
                    style: AppTextStyles.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
                Text(inv.correo,
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
                Text('Enviada: ${df.format(inv.creadaEn)}',
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.send_outlined, color: AppColors.primary),
            tooltip: 'Reenviar',
            onPressed: () => _reenviar(context),
          ),
        ],
      ),
    );
  }

  Future<void> _reenviar(BuildContext context) async {
    try {
      await ref.read(sociosRepositoryProvider).reenviarInvitacionToken(inv.id);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Invitación reenviada')),
        );
        ref.invalidate(invitacionesProvider);
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: ${unwrapDio(e)}')),
        );
      }
    }
  }
}

// ── Bottom sheet — Crear / Editar socio ────────────────────────────────────

class SocioFormSheet extends ConsumerStatefulWidget {
  const SocioFormSheet({required this.onSaved, this.socio, super.key});
  final Socio? socio;
  final VoidCallback onSaved;

  @override
  ConsumerState<SocioFormSheet> createState() => _SocioFormSheetState();
}

class _SocioFormSheetState extends ConsumerState<SocioFormSheet> {
  final _nombre = TextEditingController();
  final _apellido = TextEditingController();
  final _cedula = TextEditingController();
  final _correo = TextEditingController();
  final _telefono = TextEditingController();
  final _direccion = TextEditingController();
  final _emName = TextEditingController();
  final _emPhone = TextEditingController();
  final _emDireccion = TextEditingController();
  final _emName2 = TextEditingController();
  final _emPhone2 = TextEditingController();
  final _emDireccion2 = TextEditingController();

  // Dropdowns: almacenan el nombre del lookup (null = sin asignar).
  String? _nivel;
  String? _tipo;
  // Rol del sistema (nombre del lookup). Solo editable por Admin/Secretaria.
  String? _rol;
  String? _originalRol;
  // Estado de habilitación (nombre del lookup). Solo Admin/Secretaria.
  String? _estado;
  String? _tipoSangre;
  // Fechas en formato ISO 'yyyy-MM-dd' (lo que espera el backend).
  String? _fechaNacimiento;
  String? _fechaIngreso;
  String? _fechaSalida;
  bool _loading = false;
  bool _isDirty = false;
  String? _error;

  static const _tiposSangre = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

  List<TextEditingController> get _allControllers => [
        _nombre, _apellido, _cedula, _correo, _telefono, _direccion,
        _emName, _emPhone, _emDireccion, _emName2, _emPhone2, _emDireccion2,
      ];

  UserRole? get _userRole {
    final auth = ref.read(authNotifierProvider).asData?.value;
    return auth is AuthAuthenticated ? auth.user.rol : null;
  }

  bool get _canManageRol =>
      _userRole == UserRole.admin || _userRole == UserRole.secretaria;

  void _markDirty() {
    if (!_isDirty) setState(() => _isDirty = true);
  }

  Future<void> _tryClose() async {
    if (!_isDirty) { Navigator.of(context).pop(); return; }
    final discard = await showAppDialog(
      context: context,
      title: '¿Descartar cambios?',
      message: 'Los datos ingresados se perderán.',
      confirmLabel: 'Descartar',
      cancelLabel: 'Continuar editando',
      confirmVariant: AppButtonVariant.destructive,
    );
    if ((discard ?? false) && mounted) Navigator.of(context).pop();
  }

  @override
  void initState() {
    super.initState();
    final s = widget.socio;
    if (s != null) {
      _nombre.text = s.nombre;
      _apellido.text = s.apellido;
      _cedula.text = s.cedula ?? '';
      _correo.text = s.correo;
      _telefono.text = s.telefono ?? '';
      _direccion.text = s.direccion ?? '';
      _nivel = s.nivelTecnico;              // null = sin asignar
      _tipo = s.tipoSocio.isEmpty ? null : s.tipoSocio;
      _rol = s.rol.isEmpty ? null : s.rol;
      _originalRol = _rol;
      _estado = s.estadoHabilitacion.isEmpty ? null : s.estadoHabilitacion;
      _tipoSangre = (s.tipoSangre?.isEmpty ?? true) ? null : s.tipoSangre;
      _fechaNacimiento = s.fechaNacimiento;
      _fechaIngreso = s.fechaIngreso;
      _fechaSalida = s.fechaSalida;
      // Los contactos de emergencia solo vienen en el detalle.
      if (s is SocioDetalle) {
        _emName.text = s.emergencyContactName ?? '';
        _emPhone.text = s.emergencyContactPhone ?? '';
        _emDireccion.text = s.emergencyContactDireccion ?? '';
        _emName2.text = s.emergencyContactName2 ?? '';
        _emPhone2.text = s.emergencyContactPhone2 ?? '';
        _emDireccion2.text = s.emergencyContactDireccion2 ?? '';
      }
    }
    for (final c in _allControllers) {
      c.addListener(_markDirty);
    }
  }

  @override
  void dispose() {
    for (final c in _allControllers) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _pickDate({
    required String? current,
    required ValueChanged<String> onPicked,
  }) async {
    final now = DateTime.now();
    final initial = current != null ? DateTime.tryParse(current) ?? now : now;
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(1920),
      lastDate: DateTime(now.year + 5),
    );
    if (picked != null) {
      _markDirty();
      onPicked('${picked.year.toString().padLeft(4, '0')}-'
          '${picked.month.toString().padLeft(2, '0')}-'
          '${picked.day.toString().padLeft(2, '0')}');
    }
  }

  Widget _sectionLabel(String text) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Text(text,
            style: AppTextStyles.bodyMedium
                .copyWith(fontWeight: FontWeight.w600)),
      );

  // Validación previa al envío (espejo de las reglas del backend).
  String? _validate(bool isEdit) {
    final ced = _cedula.text.trim();
    if (!RegExp(r'^\d{10}$').hasMatch(ced)) {
      return 'La cédula debe tener exactamente 10 dígitos.';
    }
    final correo = _correo.text.trim();
    if (!RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]{2,}$').hasMatch(correo)) {
      return 'El correo no tiene un formato válido.';
    }
    for (final p in [_telefono.text, _emPhone.text, _emPhone2.text]) {
      final t = p.trim();
      if (t.isNotEmpty && !RegExp(r'^\d{1,15}$').hasMatch(t)) {
        return 'Los teléfonos solo pueden contener dígitos (máx. 15).';
      }
    }
    if (!isEdit) return null;
    if (_nombre.text.trim().isEmpty) return 'El nombre es obligatorio.';
    if (_apellido.text.trim().isEmpty) return 'El apellido es obligatorio.';
    if (_fechaNacimiento == null) {
      return 'La fecha de nacimiento es obligatoria.';
    }
    if (_tipo == null) return 'El tipo de socio es obligatorio.';
    if (_canManageRol && _estado == null) {
      return 'El estado de habilitación es obligatorio.';
    }
    return null;
  }

  Future<void> _save() async {
    final isEdit = widget.socio != null;
    final lookups = ref.read(sociosLookupsProvider).asData?.value;

    final validationError = _validate(isEdit);
    if (validationError != null) {
      setState(() => _error = validationError);
      return;
    }

    // Cadena vacía → null, para no enviar "" al backend.
    String? orNull(String v) => v.trim().isEmpty ? null : v.trim();

    final Map<String, dynamic> data;
    if (!isEdit) {
      // Alta = invitación: el backend solo acepta cédula, correo y teléfono.
      data = {
        'cedula': _cedula.text.trim(),
        'correo': _correo.text.trim(),
        'telefono': orNull(_telefono.text),
      };
    } else {
      final tipoId =
          lookups?.tipos.where((t) => t.nombre == _tipo).firstOrNull?.id;
      final nivelId = lookups?.clasificaciones
          .where((c) => c.nombre == _nivel)
          .firstOrNull
          ?.id;
      // El estado solo es editable por Admin/Secretaria; de lo contrario se
      // preserva el id actual del socio para no romper la validación NotNull.
      final estadoId = _canManageRol
          ? lookups?.estados.where((e) => e.nombre == _estado).firstOrNull?.id
          : widget.socio!.estadoHabilitacionId;
      // Se envían TODOS los campos: el backend hace replace completo y omitir
      // alguno lo borraría (pérdida de datos).
      data = {
        'nombre': _nombre.text.trim(),
        'apellido': _apellido.text.trim(),
        'cedula': _cedula.text.trim(),
        'correo': _correo.text.trim(),
        'telefono': orNull(_telefono.text),
        'direccion': orNull(_direccion.text),
        'fechaNacimiento': _fechaNacimiento,
        'fechaIngreso': _fechaIngreso,
        'fechaSalida': _fechaSalida,
        'tipoSangre': _tipoSangre,
        'emergencyContactName': orNull(_emName.text),
        'emergencyContactPhone': orNull(_emPhone.text),
        'emergencyContactDireccion': orNull(_emDireccion.text),
        'emergencyContactName2': orNull(_emName2.text),
        'emergencyContactPhone2': orNull(_emPhone2.text),
        'emergencyContactDireccion2': orNull(_emDireccion2.text),
        'tipoSocioId': tipoId,
        'nivelTecnicoId': nivelId,
        'estadoHabilitacionId': estadoId,
      };
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final repo = ref.read(sociosRepositoryProvider);
      if (!isEdit) {
        await repo.crearSocio(data);
      } else {
        await repo.editarSocio(widget.socio!.id, data);
        // El rol del sistema se cambia con un endpoint aparte (envía el id Short
        // del lookup). Solo si el usuario puede gestionarlo y cambió de valor.
        if (_canManageRol && _rol != null && _rol != _originalRol) {
          final rolId =
              lookups?.roles.where((r) => r.nombre == _rol).firstOrNull?.id;
          if (rolId != null) {
            await repo.cambiarRol(widget.socio!.id, rolId);
          }
        }
      }
      widget.onSaved();
      if (mounted) Navigator.pop(context);
    } catch (e) {
      setState(() {
        _error = unwrapDio(e).toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isEdit = widget.socio != null;
    final lookups = ref.watch(sociosLookupsProvider).asData?.value;
    final tipoItems = lookups?.tipos.map((t) => t.nombre).toList() ?? const <String>[];
    final nivelItems = lookups?.clasificaciones.map((c) => c.nombre).toList() ?? const <String>[];
    final estadoItems = lookups?.estados.map((e) => e.nombre).toList() ?? const <String>[];
    // El rol Admin no se asigna desde aquí (idéntico al filtro de la web).
    final rolItems = lookups?.roles
            .where((r) => r.nombre.toLowerCase() != 'admin')
            .map((r) => r.nombre)
            .toList() ??
        const <String>[];
    // Validar que el valor actual esté en la lista; si no, usar null.
    final tipoValue = tipoItems.contains(_tipo) ? _tipo : null;
    final nivelValue = nivelItems.contains(_nivel) ? _nivel : null;
    final estadoValue = estadoItems.contains(_estado) ? _estado : null;
    final rolValue = rolItems.contains(_rol) ? _rol : null;
    return PopScope(
      canPop: !_isDirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        await _tryClose();
      },
      child: Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
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
                Text(isEdit ? 'Editar socio' : 'Nuevo socio',
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

            if (!isEdit) ...[
              // Alta = invitación. El socio completa el resto al activar cuenta.
              Text(
                'El socio recibirá un enlace por correo para completar sus '
                'datos y crear sus credenciales de acceso.',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg, height: 1.4),
              ),
              const SizedBox(height: 16),
              AppInput(
                  controller: _cedula,
                  hint: 'Ej: 1234567890',
                  label: 'Cédula',
                  keyboardType: TextInputType.number),
              const SizedBox(height: 12),
              AppInput(
                  controller: _correo,
                  hint: 'nombre@dominio.com',
                  label: 'Correo',
                  keyboardType: TextInputType.emailAddress),
              const SizedBox(height: 12),
              AppInput(
                  controller: _telefono,
                  hint: 'Ej: 0991234567',
                  label: 'Teléfono',
                  keyboardType: TextInputType.phone),
            ] else ...[
              _sectionLabel('Datos personales'),
              Row(children: [
                Expanded(
                    child: AppInput(
                        controller: _nombre, hint: 'Nombre', label: 'Nombre')),
                const SizedBox(width: 12),
                Expanded(
                    child: AppInput(
                        controller: _apellido,
                        hint: 'Apellido',
                        label: 'Apellido')),
              ]),
              const SizedBox(height: 12),
              AppInput(
                  controller: _cedula,
                  hint: 'Cédula',
                  label: 'Cédula',
                  keyboardType: TextInputType.number),
              const SizedBox(height: 12),
              AppInput(
                  controller: _correo,
                  hint: 'Correo electrónico',
                  label: 'Correo',
                  keyboardType: TextInputType.emailAddress),
              const SizedBox(height: 12),
              AppInput(
                  controller: _telefono,
                  hint: 'Teléfono',
                  label: 'Teléfono',
                  keyboardType: TextInputType.phone),
              const SizedBox(height: 12),
              AppInput(
                  controller: _direccion,
                  hint: 'Dirección',
                  label: 'Dirección'),
              const SizedBox(height: 12),
              _DateField(
                label: 'Fecha de nacimiento',
                value: _fechaNacimiento,
                onTap: () => _pickDate(
                    current: _fechaNacimiento,
                    onPicked: (v) => setState(() => _fechaNacimiento = v)),
              ),
              const SizedBox(height: 12),
              _DateField(
                label: 'Fecha de ingreso',
                value: _fechaIngreso,
                onTap: () => _pickDate(
                    current: _fechaIngreso,
                    onPicked: (v) => setState(() => _fechaIngreso = v)),
              ),
              const SizedBox(height: 12),
              _DropdownField(
                label: 'Tipo de sangre',
                value: _tiposSangre.contains(_tipoSangre) ? _tipoSangre : null,
                nullLabel: '— Sin especificar —',
                items: _tiposSangre,
                onChanged: (v) {
                  _markDirty();
                  setState(() => _tipoSangre = v);
                },
              ),
              const SizedBox(height: 12),
              _DateField(
                label: 'Fecha de salida',
                value: _fechaSalida,
                onTap: () => _pickDate(
                    current: _fechaSalida,
                    onPicked: (v) => setState(() => _fechaSalida = v)),
              ),
              const SizedBox(height: 20),

              _sectionLabel('Contacto de emergencia 1'),
              AppInput(
                  controller: _emName, hint: 'Nombre', label: 'Nombre'),
              const SizedBox(height: 12),
              AppInput(
                  controller: _emPhone,
                  hint: 'Ej: 0991234567',
                  label: 'Teléfono',
                  keyboardType: TextInputType.phone),
              const SizedBox(height: 20),

              _sectionLabel('Contacto de emergencia 2'),
              AppInput(
                  controller: _emName2, hint: 'Nombre', label: 'Nombre'),
              const SizedBox(height: 12),
              AppInput(
                  controller: _emPhone2,
                  hint: 'Ej: 0991234567',
                  label: 'Teléfono',
                  keyboardType: TextInputType.phone),
              const SizedBox(height: 20),

              _sectionLabel('Clasificación'),
              _DropdownField(
                label: 'Nivel técnico',
                value: nivelValue,
                nullLabel: '— Sin asignar —',
                items: nivelItems,
                onChanged: (v) { _markDirty(); setState(() => _nivel = v); },
              ),
              const SizedBox(height: 12),
              _DropdownField(
                label: 'Tipo de socio',
                value: tipoValue,
                nullLabel: '— Seleccionar —',
                items: tipoItems,
                onChanged: (v) { _markDirty(); setState(() => _tipo = v); },
              ),
              if (_canManageRol) ...[
                const SizedBox(height: 12),
                _DropdownField(
                  label: 'Estado de habilitación',
                  value: estadoValue,
                  nullLabel: '— Seleccionar —',
                  items: estadoItems,
                  onChanged: (v) {
                    _markDirty();
                    setState(() => _estado = v);
                  },
                ),
                const SizedBox(height: 12),
                _DropdownField(
                  label: 'Rol en el sistema',
                  value: rolValue,
                  nullLabel: '— Seleccionar —',
                  items: rolItems,
                  onChanged: (v) { _markDirty(); setState(() => _rol = v); },
                ),
              ],
            ],

            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!,
                  style: const TextStyle(
                      color: AppColors.destructive, fontSize: 13)),
            ],
            const SizedBox(height: 24),
            AppButton(
              label: isEdit ? 'Guardar cambios' : 'Enviar invitación',
              loading: _loading,
              fullWidth: true,
              onPressed: _save,
            ),
          ],
        ),
      ),
    ),
    );
  }
}

// Campo de fecha — abre un date picker y muestra la fecha en ISO 'yyyy-MM-dd'.
class _DateField extends StatelessWidget {
  const _DateField({
    required this.label,
    required this.value,
    required this.onTap,
  });

  final String label;
  final String? value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style:
                AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
        const SizedBox(height: 4),
        InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(8),
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
                  child: Text(
                    value ?? 'Seleccionar fecha',
                    style: TextStyle(
                      color: value != null
                          ? AppColors.foreground
                          : AppColors.mutedFg,
                      fontSize: 14,
                    ),
                  ),
                ),
                const Icon(Icons.calendar_today_outlined,
                    size: 16, color: AppColors.mutedFg),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _DropdownField extends StatelessWidget {
  const _DropdownField({
    required this.label,
    required this.value,
    required this.items,
    required this.onChanged,
    this.nullLabel,
  });

  final String label;
  final String? value;
  final List<String> items;
  final ValueChanged<String?> onChanged;
  final String? nullLabel;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style:
                AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
        const SizedBox(height: 4),
        DropdownButtonFormField<String>(
          key: ValueKey(value),
          initialValue: value,
          dropdownColor: AppColors.background,
          decoration: InputDecoration(
            filled: true,
            fillColor: AppColors.secondary,
            contentPadding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide: const BorderSide(color: AppColors.border),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide: const BorderSide(color: AppColors.border),
            ),
          ),
          style: const TextStyle(color: AppColors.foreground, fontSize: 14),
          items: [
            if (nullLabel != null)
              DropdownMenuItem<String>(
                value: null,
                child: Text(nullLabel!,
                    style: const TextStyle(color: AppColors.mutedFg)),
              ),
            ...items.map((i) => DropdownMenuItem(value: i, child: Text(i))),
          ],
          onChanged: onChanged,
        ),
      ],
    );
  }
}
