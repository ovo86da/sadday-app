import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/api/app_exception.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_dialog.dart';
import '../../../../core/widgets/app_input.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../../../core/widgets/nivel_tecnico_banner.dart';
import '../../../montanas/domain/models/montana_model.dart';
import '../../../montanas/domain/models/mountain_lookups_model.dart';
import '../../../montanas/presentation/providers/montanas_provider.dart';
import '../../../salidas/presentation/providers/salidas_provider.dart';
import '../../../salidas/presentation/screens/salidas_screen.dart'
    show SalidaTipoChip, SalidaNivelChip;
import '../../../socios/domain/models/socio_model.dart' show Clasificacion;
import '../../domain/models/ruta_model.dart';
import '../providers/rutas_provider.dart';

// ── Filter state ─────────────────────────────────────────────────────────────

class _RutaFiltros {
  const _RutaFiltros({
    this.nivelMinimoId,
    this.nivelMinimoNombre,
    this.mountainId,
    this.mountainNombre,
  });

  final String? nivelMinimoId;
  final String? nivelMinimoNombre;
  final int? mountainId;
  final String? mountainNombre;

  bool get isEmpty => nivelMinimoId == null && mountainId == null;

  int get activeCount {
    int n = 0;
    if (nivelMinimoId != null) n++;
    if (mountainId != null) n++;
    return n;
  }
}

// ── Screen ────────────────────────────────────────────────────────────────────

class RutasScreen extends ConsumerStatefulWidget {
  const RutasScreen({super.key});

  @override
  ConsumerState<RutasScreen> createState() => _RutasScreenState();
}

class _RutasScreenState extends ConsumerState<RutasScreen> {
  final _searchController = TextEditingController();
  String _q = '';
  String? _tipoActividad;
  _RutaFiltros _filtros = const _RutaFiltros();
  int _listKey = 0;

  static const _actividades = <({String value, String label})>[
    (value: 'ALPINISMO', label: 'Alpinismo'),
    (value: 'TREKKING',  label: 'Trekking'),
    (value: 'ESCALADA',  label: 'Escalada'),
    (value: 'CICLISMO',  label: 'Ciclismo'),
  ];

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _setTipo(String? tipo) => setState(() {
        _tipoActividad = tipo;
        _listKey++;
      });

  void _search(String q) => setState(() {
        _q = q;
        _listKey++;
      });

  bool get _canProponer {
    final auth = ref.read(authNotifierProvider).asData?.value;
    return auth is AuthAuthenticated;
  }

  void _showProponer(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _RutaFormSheet(
        onSaved: () => setState(() => _listKey++),
      ),
    );
  }

  Future<void> _showFiltros(BuildContext context) async {
    final result = await showModalBottomSheet<_RutaFiltros>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _RutaFiltroSheet(current: _filtros),
    );
    if (result != null && mounted) {
      setState(() {
        _filtros = result;
        _listKey++;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final activeFilters = _filtros.activeCount;
    return Scaffold(
      backgroundColor: AppColors.background,
      floatingActionButton: _canProponer
          ? FloatingActionButton(
              onPressed: () => _showProponer(context),
              tooltip: 'Proponer nueva ruta',
              child: const Icon(Icons.add),
            )
          : null,
      appBar: AppBar(
        title: const Text('Rutas'),
        centerTitle: false,
        actions: [
          IconButton(
            tooltip: 'Filtrar',
            icon: activeFilters > 0
                ? Badge(
                    label: Text('$activeFilters'),
                    child: const Icon(Icons.filter_list),
                  )
                : const Icon(Icons.filter_list),
            onPressed: () => _showFiltros(context),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Buscar ruta...',
                prefixIcon: const Icon(Icons.search, size: 20),
                isDense: true,
                suffixIcon: _q.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          _searchController.clear();
                          _search('');
                        })
                    : null,
              ),
              onChanged: _search,
            ),
          ),
        ),
      ),
      body: Column(
        children: [
          const NivelTecnicoBanner(
              padding: EdgeInsets.fromLTRB(16, 12, 16, 0)),
          // Chips de tipo de actividad
          SizedBox(
            height: 48,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              children: [
                _TipoPill(
                  label: 'Todas',
                  selected: _tipoActividad == null,
                  onTap: () => _setTipo(null),
                ),
                const SizedBox(width: 8),
                ..._actividades.map((a) => Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: _TipoPill(
                        label: a.label,
                        tipo: a.value,
                        selected: _tipoActividad == a.value,
                        onTap: () => _setTipo(a.value),
                      ),
                    )),
              ],
            ),
          ),
          Expanded(
            child: AppPagedList<Ruta>(
              key: ValueKey(_listKey),
              loader: (page) => ref.read(rutasRepositoryProvider).getRutas(
                    page: page,
                    q: _q.isEmpty ? null : _q,
                    tipoActividad: _tipoActividad,
                    nivelMinimoSocioId: _filtros.nivelMinimoId,
                    mountainId: _filtros.mountainId,
                  ),
              emptyMessage: 'Sin rutas',
              itemBuilder: (_, r, _) => _RutaItem(ruta: r),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Tipo-actividad pill ───────────────────────────────────────────────────────

class _TipoPill extends StatelessWidget {
  const _TipoPill({
    required this.label,
    required this.selected,
    required this.onTap,
    this.tipo,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;
  final String? tipo;

  static const _colors = <String, Color>{
    'ALPINISMO': Color(0xFFED8936),
    'TREKKING':  Color(0xFF48BB78),
    'ESCALADA':  Color(0xFFFC8181),
    'CICLISMO':  Color(0xFF63B3ED),
  };

  @override
  Widget build(BuildContext context) {
    final color = tipo != null ? _colors[tipo] : null;
    final bg = selected
        ? (color ?? AppColors.primary)
        : (color != null
            ? color.withValues(alpha: 0.12)
            : AppColors.secondary);
    final fg = selected
        ? Colors.white
        : (color ?? AppColors.foreground);

    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: selected
                ? (color ?? AppColors.primary)
                : (color?.withValues(alpha: 0.3) ?? AppColors.border),
          ),
        ),
        child: Text(label,
            style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: fg)),
      ),
    );
  }
}

// ── Ruta list item ────────────────────────────────────────────────────────────

class _RutaItem extends StatelessWidget {
  const _RutaItem({required this.ruta});
  final Ruta ruta;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      onTap: () => context.push('/rutas/${ruta.id}'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(ruta.nombre,
                    style: AppTextStyles.bodyLarge
                        .copyWith(fontWeight: FontWeight.w600)),
              ),
              if (ruta.requierePermisos)
                const Icon(Icons.warning_amber_outlined,
                    color: AppColors.salidaPlanificada, size: 18),
            ],
          ),
          if (ruta.montanaNombre != null) ...[
            const SizedBox(height: 2),
            Row(children: [
              const Icon(Icons.landscape_outlined,
                  size: 13, color: AppColors.mutedFg),
              const SizedBox(width: 4),
              Text(ruta.montanaNombre!,
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg)),
            ]),
          ],
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            runSpacing: 4,
            children: [
              if (ruta.tipoActividad != null)
                SalidaTipoChip(tipo: ruta.tipoActividad!),
              if (ruta.nivelMinimo != null)
                SalidaNivelChip(nivel: ruta.nivelMinimo!),
              if (ruta.longitud != null)
                _NeutralChip('${ruta.longitud!.toStringAsFixed(1)} km'),
              if (ruta.desnivel != null)
                _NeutralChip('+${ruta.desnivel!.round()} m'),
            ],
          ),
        ],
      ),
    );
  }
}

class _NeutralChip extends StatelessWidget {
  const _NeutralChip(this.label);
  final String label;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: AppColors.secondary,
          borderRadius: BorderRadius.circular(4),
        ),
        child: Text(label,
            style: const TextStyle(
                color: AppColors.mutedFg,
                fontSize: 11,
                fontWeight: FontWeight.w500)),
      );
}

// ── Filter sheet ─────────────────────────────────────────────────────────────

class _RutaFiltroSheet extends ConsumerStatefulWidget {
  const _RutaFiltroSheet({required this.current});
  final _RutaFiltros current;

  @override
  ConsumerState<_RutaFiltroSheet> createState() => _RutaFiltroSheetState();
}

class _RutaFiltroSheetState extends ConsumerState<_RutaFiltroSheet> {
  late String? _nivelMinimoId;
  late String? _nivelMinimoNombre;
  late int? _mountainId;
  late String? _mountainNombre;

  @override
  void initState() {
    super.initState();
    _nivelMinimoId = widget.current.nivelMinimoId;
    _nivelMinimoNombre = widget.current.nivelMinimoNombre;
    _mountainId = widget.current.mountainId;
    _mountainNombre = widget.current.mountainNombre;
  }

  @override
  Widget build(BuildContext context) {
    final montanasAsync = ref.watch(allMontanasProvider);
    final clasifAsync = ref.watch(clasificacionesProvider);

    return Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.6,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 12),
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                    color: AppColors.border,
                    borderRadius: BorderRadius.circular(2)),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 8, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text('Filtrar rutas',
                        style: AppTextStyles.titleMedium),
                  ),
                  TextButton(
                    onPressed: () => setState(() {
                      _nivelMinimoId = null;
                      _nivelMinimoNombre = null;
                      _mountainId = null;
                      _mountainNombre = null;
                    }),
                    child: const Text('Limpiar',
                        style: TextStyle(color: AppColors.mutedFg)),
                  ),
                ],
              ),
            ),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Nivel mínimo
                    _SheetSelect(
                      label: 'Nivel mínimo de socio',
                      value: _nivelMinimoNombre,
                      placeholder: 'Cualquier nivel',
                      onTap: () async {
                        final clasif =
                            clasifAsync.asData?.value ?? const [];
                        final picked =
                            await _showPicker<Clasificacion>(
                          context: context,
                          title: 'Nivel mínimo',
                          options: clasif,
                          labelOf: (c) => c.nombre,
                          isSelected: (c) => c.id == _nivelMinimoId,
                        );
                        if (picked != null) {
                          setState(() {
                            _nivelMinimoId = picked.id;
                            _nivelMinimoNombre = picked.nombre;
                          });
                        }
                      },
                    ),
                    if (_nivelMinimoId != null) ...[
                      const SizedBox(height: 4),
                      _ClearTag(
                          label: _nivelMinimoNombre ?? '',
                          onClear: () => setState(() {
                                _nivelMinimoId = null;
                                _nivelMinimoNombre = null;
                              })),
                    ],
                    const SizedBox(height: 20),

                    // Montaña
                    _SheetSelect(
                      label: 'Montaña',
                      value: _mountainNombre,
                      placeholder: 'Buscar montaña',
                      onTap: () async {
                        final montanas =
                            montanasAsync.asData?.value ?? const [];
                        final picked =
                            await _showPicker<Montana>(
                          context: context,
                          title: 'Montaña',
                          options: montanas,
                          labelOf: (m) => m.nombre,
                          subtitleOf: (m) => m.altitud != null
                              ? '${m.altitud!.toStringAsFixed(0)} msnm'
                              : null,
                          isSelected: (m) => m.id == _mountainId,
                          searchable: true,
                        );
                        if (picked != null) {
                          setState(() {
                            _mountainId = picked.id;
                            _mountainNombre = picked.nombre;
                          });
                        }
                      },
                    ),
                    if (_mountainId != null) ...[
                      const SizedBox(height: 4),
                      _ClearTag(
                          label: _mountainNombre ?? '',
                          onClear: () => setState(() {
                                _mountainId = null;
                                _mountainNombre = null;
                              })),
                    ],
                    const SizedBox(height: 32),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
              child: AppButton(
                label: 'Aplicar filtros',
                fullWidth: true,
                onPressed: () => Navigator.pop(
                  context,
                  _RutaFiltros(
                    nivelMinimoId: _nivelMinimoId,
                    nivelMinimoNombre: _nivelMinimoNombre,
                    mountainId: _mountainId,
                    mountainNombre: _mountainNombre,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<T?> _showPicker<T>({
    required BuildContext context,
    required String title,
    required List<T> options,
    required String Function(T) labelOf,
    String? Function(T)? subtitleOf,
    bool Function(T)? isSelected,
    bool searchable = false,
  }) {
    return showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _PickerSheet<T>(
        title: title,
        options: options,
        labelOf: labelOf,
        subtitleOf: subtitleOf,
        isSelected: isSelected,
        searchable: searchable,
      ),
    );
  }
}

// ── Reusable sheet widgets ────────────────────────────────────────────────────

class _SheetSelect extends StatelessWidget {
  const _SheetSelect({
    required this.label,
    required this.value,
    required this.placeholder,
    required this.onTap,
  });

  final String label;
  final String? value;
  final String placeholder;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final hasValue = value != null && value!.isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
        const SizedBox(height: 4),
        GestureDetector(
          onTap: onTap,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
            decoration: BoxDecoration(
              color: AppColors.secondary,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    hasValue ? value! : placeholder,
                    style: TextStyle(
                        color:
                            hasValue ? AppColors.foreground : AppColors.mutedFg,
                        fontSize: 14),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const Icon(Icons.keyboard_arrow_down,
                    size: 18, color: AppColors.mutedFg),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _ClearTag extends StatelessWidget {
  const _ClearTag({required this.label, required this.onClear});
  final String label;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onClear,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.primary.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(20),
          border:
              Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(label,
                style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.primary,
                    fontWeight: FontWeight.w500)),
            const SizedBox(width: 4),
            const Icon(Icons.close, size: 13, color: AppColors.primary),
          ],
        ),
      ),
    );
  }
}

class _PickerSheet<T> extends StatefulWidget {
  const _PickerSheet({
    required this.title,
    required this.options,
    required this.labelOf,
    this.subtitleOf,
    this.isSelected,
    this.searchable = false,
  });

  final String title;
  final List<T> options;
  final String Function(T) labelOf;
  final String? Function(T)? subtitleOf;
  final bool Function(T)? isSelected;
  final bool searchable;

  @override
  State<_PickerSheet<T>> createState() => _PickerSheetState<T>();
}

class _PickerSheetState<T> extends State<_PickerSheet<T>> {
  final _search = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _query.isEmpty
        ? widget.options
        : widget.options
            .where((o) =>
                widget.labelOf(o).toLowerCase().contains(_query))
            .toList();

    return Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.7,
        child: Column(
          children: [
            const SizedBox(height: 12),
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                    color: AppColors.border,
                    borderRadius: BorderRadius.circular(2)),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(widget.title, style: AppTextStyles.titleMedium),
              ),
            ),
            if (widget.searchable)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
                child: AppInput(
                  controller: _search,
                  hint: 'Buscar...',
                  prefixIcon: Icons.search,
                  autofocus: true,
                  onChanged: (v) =>
                      setState(() => _query = v.trim().toLowerCase()),
                ),
              ),
            Expanded(
              child: filtered.isEmpty
                  ? const Center(
                      child: Text('Sin resultados',
                          style: TextStyle(color: AppColors.mutedFg)))
                  : ListView.builder(
                      itemCount: filtered.length,
                      itemBuilder: (_, i) {
                        final o = filtered[i];
                        final sel = widget.isSelected?.call(o) ?? false;
                        final sub = widget.subtitleOf?.call(o);
                        return ListTile(
                          title: Text(widget.labelOf(o),
                              style: TextStyle(
                                  color: sel
                                      ? AppColors.primary
                                      : AppColors.foreground)),
                          subtitle: sub != null
                              ? Text(sub,
                                  style: const TextStyle(
                                      color: AppColors.mutedFg,
                                      fontSize: 12))
                              : null,
                          trailing: sel
                              ? const Icon(Icons.check,
                                  color: AppColors.primary, size: 18)
                              : null,
                          onTap: () => Navigator.pop(context, o),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Formulario: Proponer nueva ruta ──────────────────────────────────────────

class _RutaFormSheet extends ConsumerStatefulWidget {
  const _RutaFormSheet({required this.onSaved});
  final VoidCallback onSaved;

  @override
  ConsumerState<_RutaFormSheet> createState() => _RutaFormSheetState();
}

class _RutaFormSheetState extends ConsumerState<_RutaFormSheet> {
  final _formKey = GlobalKey<FormState>();

  final _nombre = TextEditingController();
  final _lugar = TextEditingController();
  final _sectorZona = TextEditingController();
  final _longitud = TextEditingController();
  final _desnivel = TextEditingController();
  final _duracionHoras = TextEditingController();
  final _duracionDias = TextEditingController();
  final _notas = TextEditingController();
  final _documentacionUrl = TextEditingController();
  final _trackUrl = TextEditingController();
  final _numCintas = TextEditingController();
  final _alturaViaM = TextEditingController();
  final _tipoRoca = TextEditingController();
  final _tipoTerreno = TextEditingController();
  final _superficiePredominante = TextEditingController();
  final _ciclabilidadPct = TextEditingController();

  String _tipoActividad = 'ALPINISMO';
  int? _mountainId;
  String? _mountainNombre;
  String? _nivelId;
  String? _nivelNombre;
  bool _requierePermisos = false;

  // Alpinismo + Escalada (campo compartido)
  String? _dificultadRocaId;
  String? _dificultadRocaNombre;

  // Alpinismo
  String? _escalaAlpinaId;
  String? _escalaAlpinaNombre;
  String? _dificultadHieloId;
  String? _dificultadHieloNombre;
  String? _compromisoId;
  String? _compromisoNombre;
  String? _yosemiteId;
  String? _yosemiteNombre;
  String? _saddayNivelTecnicoId;
  String? _saddayNivelTecnicoNombre;
  String? _saddayNivelFisicoId;
  String? _saddayNivelFisicoNombre;
  int? _equipoMontanaId;
  String? _equipoMontanaNombre;

  // Escalada
  String? _tipoEscalada;

  // Trekking
  String? _dificultadSenderismoId;
  String? _dificultadSenderismoNombre;
  bool _esCircular = false;
  bool _fuentesAgua = false;

  // Ciclismo
  String? _tipoBicicleta;
  String? _dificultadTecnicaCiclismo;

  bool _saving = false;
  bool _isDirty = false;
  String? _error;

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

  static const _tipos = [
    (value: 'ALPINISMO', label: 'Alpinismo'),
    (value: 'ESCALADA',  label: 'Escalada'),
    (value: 'TREKKING',  label: 'Trekking'),
    (value: 'CICLISMO',  label: 'Ciclismo'),
  ];

  static const _tiposEscalada = ['DEPORTIVA', 'TRADICIONAL', 'MIXTA', 'BOULDER'];
  static const _tiposEscaladaLabels = <String, String>{
    'DEPORTIVA': 'Deportiva', 'TRADICIONAL': 'Tradicional', 'MIXTA': 'Mixta', 'BOULDER': 'Boulder',
  };
  static const _tiposBicicleta = ['RIGIDA', 'DOBLE_SUSPENSION', 'ENDURO', 'GRAVEL', 'RUTA'];
  static const _tipoBicicletaLabels = <String, String>{
    'RIGIDA': 'Rígida', 'DOBLE_SUSPENSION': 'Doble suspensión',
    'ENDURO': 'Enduro', 'GRAVEL': 'Gravel', 'RUTA': 'Ruta',
  };
  static const _dificultadesCiclismo = ['S0', 'S1', 'S2', 'S3', 'S4'];

  @override
  void initState() {
    super.initState();
    for (final c in [
      _nombre, _lugar, _sectorZona, _longitud, _desnivel,
      _duracionHoras, _duracionDias, _notas, _documentacionUrl, _trackUrl,
      _numCintas, _alturaViaM, _tipoRoca, _tipoTerreno,
      _superficiePredominante, _ciclabilidadPct,
    ]) {
      c.addListener(_markDirty);
    }
  }

  @override
  void dispose() {
    _nombre.dispose(); _lugar.dispose(); _sectorZona.dispose();
    _longitud.dispose(); _desnivel.dispose();
    _duracionHoras.dispose(); _duracionDias.dispose();
    _notas.dispose(); _documentacionUrl.dispose(); _trackUrl.dispose();
    _numCintas.dispose(); _alturaViaM.dispose(); _tipoRoca.dispose();
    _tipoTerreno.dispose(); _superficiePredominante.dispose(); _ciclabilidadPct.dispose();
    super.dispose();
  }

  String? _validateTipoSpecific() {
    if (_tipoActividad == 'ALPINISMO') {
      if (_mountainId == null) return 'La montaña es requerida para alpinismo.';
      if (_escalaAlpinaId == null) return 'Selecciona la Escala Alpina IFAS.';
      if (_dificultadRocaId == null) return 'Selecciona la Dificultad de Roca.';
      if (_dificultadHieloId == null) return 'Selecciona la Dificultad de Hielo.';
      if (_compromisoId == null) return 'Selecciona el Compromiso.';
      if (_yosemiteId == null) return 'Selecciona la clase Yosemite.';
      if (_saddayNivelTecnicoId == null) return 'Selecciona el Sadday Nivel Técnico.';
      if (_saddayNivelFisicoId == null) return 'Selecciona el Sadday Nivel Físico.';
    } else if (_tipoActividad == 'ESCALADA') {
      if (_dificultadRocaId == null) return 'Selecciona el Grado de Roca.';
      if (_tipoEscalada == null) return 'Selecciona el Tipo de Escalada.';
    } else if (_tipoActividad == 'TREKKING') {
      if (_dificultadSenderismoId == null) return 'Selecciona la Dificultad del trekking.';
    } else if (_tipoActividad == 'CICLISMO') {
      if (_tipoBicicleta == null) return 'Selecciona el Tipo de Bicicleta.';
    }
    if (_mountainId == null && _lugar.text.trim().isEmpty) {
      return 'Indica una montaña o un lugar de referencia.';
    }
    return null;
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final tipoError = _validateTipoSpecific();
    if (tipoError != null) {
      setState(() => _error = tipoError);
      return;
    }
    setState(() { _saving = true; _error = null; });
    try {
      await ref.read(rutasRepositoryProvider).crearRuta(<String, dynamic>{
        'nombre': _nombre.text.trim(),
        'tipoActividad': _tipoActividad,
        'requierePermisos': _requierePermisos,
        if (_mountainId != null) 'mountainId': _mountainId,
        if (_lugar.text.isNotEmpty) 'lugarReferencia': _lugar.text.trim(),
        if (_sectorZona.text.isNotEmpty) 'sectorZona': _sectorZona.text.trim(),
        if (_longitud.text.isNotEmpty) 'longitudKm': double.tryParse(_longitud.text),
        if (_desnivel.text.isNotEmpty) 'desnivelM': int.tryParse(_desnivel.text),
        if (_tipoActividad == 'ALPINISMO' && _duracionDias.text.isNotEmpty)
          'duracionDias': int.tryParse(_duracionDias.text),
        if (_tipoActividad != 'ALPINISMO' && _duracionHoras.text.isNotEmpty)
          'duracionHoras': int.tryParse(_duracionHoras.text),
        if (_notas.text.isNotEmpty) 'peligrosNotas': _notas.text.trim(),
        if (_nivelId != null) 'nivelMinimoSocioId': _nivelId,
        if (_documentacionUrl.text.isNotEmpty) 'documentacionUrl': _documentacionUrl.text.trim(),
        if (_trackUrl.text.isNotEmpty) 'trackUrl': _trackUrl.text.trim(),
        if (_tipoActividad == 'ALPINISMO') ...{
          'escalaAlpinaIfasId': _escalaAlpinaId,
          'dificultadRocaId': _dificultadRocaId,
          'dificultadHieloId': _dificultadHieloId,
          'compromisoId': _compromisoId,
          'yosemiteId': _yosemiteId,
          'saddayNivelTecnicoId': _saddayNivelTecnicoId,
          'saddayNivelFisicoId': _saddayNivelFisicoId,
          if (_equipoMontanaId != null) 'equipoMontanaId': _equipoMontanaId,
        },
        if (_tipoActividad == 'ESCALADA') ...{
          'dificultadRocaId': _dificultadRocaId,
          'tipoEscalada': _tipoEscalada,
          if (_numCintas.text.isNotEmpty) 'numCintas': int.tryParse(_numCintas.text),
          if (_alturaViaM.text.isNotEmpty) 'alturaViaM': int.tryParse(_alturaViaM.text),
          if (_tipoRoca.text.isNotEmpty) 'tipoRoca': _tipoRoca.text.trim(),
        },
        if (_tipoActividad == 'TREKKING') ...{
          'dificultadSenderismoId': _dificultadSenderismoId,
          'esCircular': _esCircular,
          'fuentesAgua': _fuentesAgua,
          if (_tipoTerreno.text.isNotEmpty) 'tipoTerreno': _tipoTerreno.text.trim(),
        },
        if (_tipoActividad == 'CICLISMO') ...{
          'tipoBicicleta': _tipoBicicleta,
          if (_dificultadTecnicaCiclismo != null) 'dificultadTecnicaCiclismo': _dificultadTecnicaCiclismo,
          if (_superficiePredominante.text.isNotEmpty) 'superficiePredominante': _superficiePredominante.text.trim(),
          if (_ciclabilidadPct.text.isNotEmpty) 'ciclabilidadPct': double.tryParse(_ciclabilidadPct.text),
        },
      });
      if (mounted) {
        Navigator.pop(context);
        widget.onSaved();
      }
    } catch (e) {
      setState(() => _error = unwrapDio(e).toString());
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final montanasAsync = ref.watch(allMontanasProvider);
    final clasifAsync = ref.watch(clasificacionesProvider);
    final lookups = ref.watch(mountainLookupsProvider).asData?.value;
    final montanaLabel = _tipoActividad == 'ALPINISMO' ? 'Montaña *' : 'Montaña (opcional)';

    return PopScope(
      canPop: !_isDirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        await _tryClose();
      },
      child: Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.92,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 12),
            Center(
              child: Container(
                width: 36, height: 4,
                decoration: BoxDecoration(color: AppColors.border, borderRadius: BorderRadius.circular(2)),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 4, 0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Proponer nueva ruta', style: AppTextStyles.titleMedium.copyWith(fontWeight: FontWeight.w700)),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20),
                    onPressed: _tryClose,
                    color: AppColors.mutedFg,
                  ),
                ],
              ),
            ),
            Expanded(
              child: Form(
                key: _formKey,
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [

                      // ── Tipo de actividad ─────────────────────────
                      _sectionLabel('Tipo de actividad'),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8, runSpacing: 8,
                        children: _tipos.map((t) => ChoiceChip(
                          label: Text(t.label),
                          selected: _tipoActividad == t.value,
                          onSelected: (_) { if (t.value != _tipoActividad) _markDirty(); setState(() => _tipoActividad = t.value); },
                          selectedColor: AppColors.primary.withValues(alpha: 0.2),
                          checkmarkColor: AppColors.primary,
                          labelStyle: TextStyle(
                            color: _tipoActividad == t.value ? AppColors.primary : AppColors.foreground,
                            fontSize: 13,
                          ),
                        )).toList(),
                      ),
                      const SizedBox(height: 24),

                      // ── Datos generales ───────────────────────────
                      _sectionLabel('Datos generales'),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _nombre,
                        decoration: const InputDecoration(labelText: 'Nombre *'),
                        validator: (v) => (v == null || v.trim().isEmpty) ? 'Requerido' : null,
                      ),
                      const SizedBox(height: 12),
                      _FormSelect(
                        label: montanaLabel,
                        value: _mountainNombre,
                        placeholder: 'Seleccionar montaña',
                        onTap: () async {
                          final opts = montanasAsync.asData?.value ?? [];
                          final picked = await _showPicker<Montana>(context,
                            title: 'Montaña', options: opts,
                            labelOf: (m) => m.nombre,
                            subtitleOf: (m) => m.altitud != null ? '${m.altitud!.round()} msnm' : null,
                            isSelected: (m) => m.id == _mountainId, searchable: true,
                          );
                          if (picked != null) setState(() { _mountainId = picked.id; _mountainNombre = picked.nombre; });
                        },
                        onClear: _mountainId == null ? null : () => setState(() { _mountainId = null; _mountainNombre = null; }),
                      ),
                      if (_tipoActividad == 'TREKKING' || _tipoActividad == 'CICLISMO' || _mountainId == null) ...[
                        const SizedBox(height: 12),
                        AppInput(
                          controller: _lugar,
                          label: _mountainId == null ? 'Lugar / Zona de referencia *' : 'Lugar / Zona de referencia',
                          hint: 'Ej. Quilotoa Loop, Cotopaxi Norte...',
                        ),
                      ],
                      const SizedBox(height: 12),
                      AppInput(controller: _sectorZona, label: 'Sector / Zona'),
                      const SizedBox(height: 12),
                      Row(children: [
                        Expanded(child: AppInput(
                          controller: _longitud, label: 'Longitud (km)', hint: 'Ej. 12.5',
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        )),
                        const SizedBox(width: 12),
                        Expanded(child: AppInput(
                          controller: _desnivel, label: 'Desnivel (m)', hint: 'Ej. 1200',
                          keyboardType: TextInputType.number,
                        )),
                      ]),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _tipoActividad == 'ALPINISMO' ? _duracionDias : _duracionHoras,
                        label: _tipoActividad == 'ALPINISMO' ? 'Duración (días)' : 'Duración (horas)',
                        hint: _tipoActividad == 'ALPINISMO' ? 'Ej. 2' : 'Ej. 8',
                        keyboardType: TextInputType.number,
                      ),
                      const SizedBox(height: 12),
                      _FormSelect(
                        label: 'Nivel mínimo de socio (opcional)',
                        value: _nivelNombre, placeholder: 'Sin restricción',
                        onTap: () async {
                          final opts = clasifAsync.asData?.value ?? [];
                          final picked = await _showPicker<Clasificacion>(context,
                            title: 'Nivel mínimo', options: opts,
                            labelOf: (c) => c.nombre, isSelected: (c) => c.id == _nivelId,
                          );
                          if (picked != null) setState(() { _nivelId = picked.id; _nivelNombre = picked.nombre; });
                        },
                        onClear: _nivelId == null ? null : () => setState(() { _nivelId = null; _nivelNombre = null; }),
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _notas, label: 'Peligros / Notas',
                        hint: 'Condiciones, peligros, recomendaciones...', maxLines: 3,
                      ),

                      // ── ALPINISMO ─────────────────────────────────
                      if (_tipoActividad == 'ALPINISMO') ...[
                        const SizedBox(height: 24),
                        _sectionLabel('Dificultad técnica (Alpinismo)'),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Escala Alpina IFAS *', value: _escalaAlpinaNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<EscalaAlpina>(context,
                              title: 'Escala Alpina IFAS', options: lookups?.escalasAlpina ?? [],
                              labelOf: (e) => '${e.grado} — ${e.nombre}', isSelected: (e) => e.id == _escalaAlpinaId,
                            );
                            if (picked != null) setState(() { _escalaAlpinaId = picked.id; _escalaAlpinaNombre = '${picked.grado} — ${picked.nombre}'; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Dificultad Roca *', value: _dificultadRocaNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<DificultadRoca>(context,
                              title: 'Dificultad de Roca', options: lookups?.dificultadesRoca ?? [],
                              labelOf: (r) => '${r.uiaa} (${r.francesa})', isSelected: (r) => r.id == _dificultadRocaId,
                            );
                            if (picked != null) setState(() { _dificultadRocaId = picked.id; _dificultadRocaNombre = '${picked.uiaa} (${picked.francesa})'; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Dificultad Hielo *', value: _dificultadHieloNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<DificultadHielo>(context,
                              title: 'Dificultad de Hielo', options: lookups?.dificultadesHielo ?? [],
                              labelOf: (h) => h.grado, isSelected: (h) => h.id == _dificultadHieloId,
                            );
                            if (picked != null) setState(() { _dificultadHieloId = picked.id; _dificultadHieloNombre = picked.grado; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Compromiso *', value: _compromisoNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<Compromiso>(context,
                              title: 'Compromiso', options: lookups?.compromisos ?? [],
                              labelOf: (c) => c.tipo, isSelected: (c) => c.id == _compromisoId,
                            );
                            if (picked != null) setState(() { _compromisoId = picked.id; _compromisoNombre = picked.tipo; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Yosemite *', value: _yosemiteNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<YosemiteClase>(context,
                              title: 'Clase Yosemite', options: lookups?.yosemiteClases ?? [],
                              labelOf: (y) => y.tipo, isSelected: (y) => y.id == _yosemiteId,
                            );
                            if (picked != null) setState(() { _yosemiteId = picked.id; _yosemiteNombre = picked.tipo; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Sadday Nivel Técnico *', value: _saddayNivelTecnicoNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<SaddayRiesgo>(context,
                              title: 'Sadday Nivel Técnico', options: lookups?.saddayRiesgos ?? [],
                              labelOf: (s) => s.escala, isSelected: (s) => s.id == _saddayNivelTecnicoId,
                            );
                            if (picked != null) setState(() { _saddayNivelTecnicoId = picked.id; _saddayNivelTecnicoNombre = picked.escala; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Sadday Nivel Físico *', value: _saddayNivelFisicoNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<SaddayRiesgo>(context,
                              title: 'Sadday Nivel Físico', options: lookups?.saddayRiesgos ?? [],
                              labelOf: (s) => s.escala, isSelected: (s) => s.id == _saddayNivelFisicoId,
                            );
                            if (picked != null) setState(() { _saddayNivelFisicoId = picked.id; _saddayNivelFisicoNombre = picked.escala; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Equipo recomendado', value: _equipoMontanaNombre, placeholder: 'Sin especificar',
                          onTap: () async {
                            final picked = await _showPicker<EquipoMontana>(context,
                              title: 'Equipo recomendado', options: lookups?.equipos ?? [],
                              labelOf: (e) => e.nombre, isSelected: (e) => e.id == _equipoMontanaId,
                            );
                            if (picked != null) setState(() { _equipoMontanaId = picked.id; _equipoMontanaNombre = picked.nombre; });
                          },
                          onClear: _equipoMontanaId == null ? null : () => setState(() { _equipoMontanaId = null; _equipoMontanaNombre = null; }),
                        ),
                      ],

                      // ── ESCALADA ──────────────────────────────────
                      if (_tipoActividad == 'ESCALADA') ...[
                        const SizedBox(height: 24),
                        _sectionLabel('Dificultad técnica (Escalada)'),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Grado de roca *', value: _dificultadRocaNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<DificultadRoca>(context,
                              title: 'Grado de Roca', options: lookups?.dificultadesRoca ?? [],
                              labelOf: (r) => '${r.uiaa} (${r.francesa})', isSelected: (r) => r.id == _dificultadRocaId,
                            );
                            if (picked != null) setState(() { _dificultadRocaId = picked.id; _dificultadRocaNombre = '${picked.uiaa} (${picked.francesa})'; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Tipo de escalada *',
                          value: _tipoEscalada != null ? _tiposEscaladaLabels[_tipoEscalada] : null,
                          placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<String>(context,
                              title: 'Tipo de escalada', options: _tiposEscalada,
                              labelOf: (t) => _tiposEscaladaLabels[t] ?? t, isSelected: (t) => t == _tipoEscalada,
                            );
                            if (picked != null) setState(() => _tipoEscalada = picked);
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        Row(children: [
                          Expanded(child: AppInput(controller: _numCintas, label: 'N° de cintas', keyboardType: TextInputType.number)),
                          const SizedBox(width: 12),
                          Expanded(child: AppInput(controller: _alturaViaM, label: 'Altura vía (m)', keyboardType: TextInputType.number)),
                        ]),
                        const SizedBox(height: 12),
                        AppInput(controller: _tipoRoca, label: 'Tipo de roca', hint: 'Basalto, granito, caliza...'),
                      ],

                      // ── TREKKING ──────────────────────────────────
                      if (_tipoActividad == 'TREKKING') ...[
                        const SizedBox(height: 24),
                        _sectionLabel('Características del trekking'),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Dificultad *', value: _dificultadSenderismoNombre, placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<DificultadSenderismo>(context,
                              title: 'Dificultad', options: lookups?.dificultadesSenderismo ?? [],
                              labelOf: (d) => d.nombre, isSelected: (d) => d.id == _dificultadSenderismoId,
                            );
                            if (picked != null) setState(() { _dificultadSenderismoId = picked.id; _dificultadSenderismoNombre = picked.nombre; });
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        AppInput(controller: _tipoTerreno, label: 'Tipo de terreno', hint: 'Sendero, páramo, bosque...'),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: '¿Ruta circular?',
                          value: _esCircular ? 'Sí (circular)' : 'No (ida y vuelta)',
                          placeholder: 'No (ida y vuelta)',
                          onTap: () async {
                            final picked = await _showPicker<bool>(context,
                              title: '¿Ruta circular?', options: [false, true],
                              labelOf: (b) => b ? 'Sí (circular)' : 'No (ida y vuelta)',
                              isSelected: (b) => b == _esCircular,
                            );
                            if (picked != null) setState(() => _esCircular = picked);
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: '¿Fuentes de agua?',
                          value: _fuentesAgua ? 'Sí' : 'No',
                          placeholder: 'No',
                          onTap: () async {
                            final picked = await _showPicker<bool>(context,
                              title: '¿Fuentes de agua?', options: [false, true],
                              labelOf: (b) => b ? 'Sí' : 'No', isSelected: (b) => b == _fuentesAgua,
                            );
                            if (picked != null) setState(() => _fuentesAgua = picked);
                          },
                          onClear: null,
                        ),
                      ],

                      // ── CICLISMO ──────────────────────────────────
                      if (_tipoActividad == 'CICLISMO') ...[
                        const SizedBox(height: 24),
                        _sectionLabel('Características del ciclismo'),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Tipo de bicicleta *',
                          value: _tipoBicicleta != null ? (_tipoBicicletaLabels[_tipoBicicleta] ?? _tipoBicicleta) : null,
                          placeholder: 'Seleccionar...',
                          onTap: () async {
                            final picked = await _showPicker<String>(context,
                              title: 'Tipo de bicicleta', options: _tiposBicicleta,
                              labelOf: (t) => _tipoBicicletaLabels[t] ?? t, isSelected: (t) => t == _tipoBicicleta,
                            );
                            if (picked != null) setState(() => _tipoBicicleta = picked);
                          },
                          onClear: null,
                        ),
                        const SizedBox(height: 12),
                        _FormSelect(
                          label: 'Dificultad técnica (Singletrail)',
                          value: _dificultadTecnicaCiclismo, placeholder: 'Sin clasificar',
                          onTap: () async {
                            final picked = await _showPicker<String>(context,
                              title: 'Dificultad técnica', options: _dificultadesCiclismo,
                              labelOf: (d) => d, isSelected: (d) => d == _dificultadTecnicaCiclismo,
                            );
                            if (picked != null) setState(() => _dificultadTecnicaCiclismo = picked);
                          },
                          onClear: _dificultadTecnicaCiclismo == null ? null : () => setState(() => _dificultadTecnicaCiclismo = null),
                        ),
                        const SizedBox(height: 12),
                        AppInput(controller: _superficiePredominante, label: 'Superficie predominante', hint: 'Lastrado, singletrack, empedrado...'),
                        const SizedBox(height: 12),
                        AppInput(
                          controller: _ciclabilidadPct, label: 'Ciclabilidad (%)', hint: '0–100',
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        ),
                      ],

                      // ── Información adicional ─────────────────────
                      const SizedBox(height: 24),
                      _sectionLabel('Información adicional'),
                      const SizedBox(height: 4),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('Requiere permisos especiales'),
                        value: _requierePermisos,
                        onChanged: (v) => setState(() => _requierePermisos = v),
                        activeThumbColor: AppColors.primary,
                      ),
                      AppInput(controller: _documentacionUrl, label: 'URL Documentación'),
                      const SizedBox(height: 12),
                      AppInput(controller: _trackUrl, label: 'URL Track GPS', hint: 'Wikiloc, Komoot, AllTrails, Strava...'),

                      if (_error != null) ...[
                        const SizedBox(height: 12),
                        Text(_error!, style: const TextStyle(color: AppColors.destructive, fontSize: 13)),
                      ],
                      const SizedBox(height: 8),
                    ],
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
              child: AppButton(label: 'Proponer ruta', fullWidth: true, loading: _saving, onPressed: _saving ? null : _submit),
            ),
          ],
        ),
      ),
    ),
    );
  }

  Widget _sectionLabel(String label) => Text(label,
      style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg, fontWeight: FontWeight.w600));

  Future<T?> _showPicker<T>(
    BuildContext context, {
    required String title,
    required List<T> options,
    required String Function(T) labelOf,
    String? Function(T)? subtitleOf,
    bool Function(T)? isSelected,
    bool searchable = false,
  }) =>
      showModalBottomSheet<T>(
        context: context,
        isScrollControlled: true,
        backgroundColor: AppColors.background,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
        ),
        builder: (_) => _PickerSheet<T>(
          title: title,
          options: options,
          labelOf: labelOf,
          subtitleOf: subtitleOf,
          isSelected: isSelected,
          searchable: searchable,
        ),
      );
}

class _FormSelect extends StatelessWidget {
  const _FormSelect({
    required this.label,
    required this.value,
    required this.placeholder,
    required this.onTap,
    this.onClear,
  });

  final String label;
  final String? value;
  final String placeholder;
  final VoidCallback onTap;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    final hasValue = value != null && value!.isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style:
                AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
        const SizedBox(height: 4),
        GestureDetector(
          onTap: onTap,
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
            decoration: BoxDecoration(
              color: AppColors.secondary,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    hasValue ? value! : placeholder,
                    style: TextStyle(
                        color: hasValue
                            ? AppColors.foreground
                            : AppColors.mutedFg,
                        fontSize: 14),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (hasValue && onClear != null)
                  GestureDetector(
                    onTap: onClear,
                    child: const Icon(Icons.close,
                        size: 16, color: AppColors.mutedFg),
                  )
                else
                  const Icon(Icons.keyboard_arrow_down,
                      size: 18, color: AppColors.mutedFg),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
