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
      await ref.read(sociosRepositoryProvider).reenviarInvitacion(inv.id);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Invitación reenviada')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
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
  String _nivel = 'BASICO';
  String _tipo = 'ACTIVO';
  bool _loading = false;
  String? _error;

  static const _niveles = ['BASICO', 'INTERMEDIO', 'AVANZADO', 'EXPERTO'];
  static const _tipos = ['ACTIVO', 'PASIVO', 'HONORARIO'];

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
      _nivel = s.nivelTecnico ?? 'BASICO';
      _tipo = s.tipoSocio.isEmpty ? 'ACTIVO' : s.tipoSocio;
    }
  }

  @override
  void dispose() {
    _nombre.dispose();
    _apellido.dispose();
    _cedula.dispose();
    _correo.dispose();
    _telefono.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final data = {
      'nombre': _nombre.text.trim(),
      'apellido': _apellido.text.trim(),
      'cedula': _cedula.text.trim(),
      'correo': _correo.text.trim(),
      'telefono': _telefono.text.trim(),
      'nivelTecnico': _nivel,
      'tipoSocio': _tipo,
    };

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final repo = ref.read(sociosRepositoryProvider);
      if (widget.socio == null) {
        await repo.crearSocio(data);
      } else {
        await repo.editarSocio(widget.socio!.id, data);
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
    return Padding(
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
            Text(isEdit ? 'Editar socio' : 'Nuevo socio',
                style: AppTextStyles.titleMedium),
            const SizedBox(height: 20),
            Row(children: [
              Expanded(
                  child: AppInput(
                      controller: _nombre,
                      hint: 'Nombre',
                      label: 'Nombre')),
              const SizedBox(width: 12),
              Expanded(
                  child: AppInput(
                      controller: _apellido,
                      hint: 'Apellido',
                      label: 'Apellido')),
            ]),
            const SizedBox(height: 12),
            AppInput(
                controller: _cedula, hint: 'Cédula', label: 'Cédula'),
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
            _DropdownField(
              label: 'Nivel técnico',
              value: _nivel,
              items: _niveles,
              onChanged: (v) => setState(() => _nivel = v!),
            ),
            const SizedBox(height: 12),
            _DropdownField(
              label: 'Tipo de socio',
              value: _tipo,
              items: _tipos,
              onChanged: (v) => setState(() => _tipo = v!),
            ),
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
              onPressed: _save,
            ),
          ],
        ),
      ),
    );
  }
}

class _DropdownField extends StatelessWidget {
  const _DropdownField({
    required this.label,
    required this.value,
    required this.items,
    required this.onChanged,
  });

  final String label;
  final String value;
  final List<String> items;
  final ValueChanged<String?> onChanged;

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
          items: items
              .map((i) => DropdownMenuItem(value: i, child: Text(i)))
              .toList(),
          onChanged: onChanged,
        ),
      ],
    );
  }
}
