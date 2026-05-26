import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_input.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../../montanas/domain/models/montana_model.dart';
import '../../../../core/widgets/nivel_tecnico_banner.dart';
import '../../../salidas/presentation/providers/salidas_provider.dart';
import '../../../socios/domain/models/socio_model.dart' show Clasificacion;
import '../../../salidas/presentation/screens/salidas_screen.dart'
    show SalidaTipoChip, SalidaNivelChip;
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
