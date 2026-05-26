import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../../core/widgets/app_input.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../../../core/widgets/app_status_badge.dart';
import '../../../estadisticas/domain/models/estadisticas_models.dart';
import '../../../estadisticas/presentation/providers/estadisticas_provider.dart';
import '../../../montanas/domain/models/montana_model.dart';
import '../../../montanas/presentation/providers/montanas_provider.dart';
import '../../../rutas/domain/models/ruta_model.dart';
import '../../../rutas/presentation/providers/rutas_provider.dart';
import '../../../socios/domain/models/socio_model.dart';
import '../../domain/models/salida_model.dart';
import '../providers/salidas_provider.dart';

class SalidasScreen extends ConsumerStatefulWidget {
  const SalidasScreen({super.key});

  @override
  ConsumerState<SalidasScreen> createState() => _SalidasScreenState();
}

class _SalidasScreenState extends ConsumerState<SalidasScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  int _tabKey = 0;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this)
      ..addListener(() {
        if (!_tabController.indexIsChanging) setState(() => _tabKey++);
      });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  bool get _canCreate {
    final auth = ref.read(authNotifierProvider).asData?.value;
    if (auth is! AuthAuthenticated) return false;
    return auth.user.rol == UserRole.admin ||
        auth.user.rol == UserRole.secretaria ||
        auth.user.rol == UserRole.directivo;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Salidas'),
        centerTitle: false,
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Todas'),
            Tab(text: 'Próximas'),
            Tab(text: 'Mis Salidas'),
          ],
        ),
      ),
      floatingActionButton: _canCreate
          ? FloatingActionButton(
              onPressed: () => _showSalidaForm(context),
              child: const Icon(Icons.add),
            )
          : null,
      body: TabBarView(
        controller: _tabController,
        children: [
          _SalidasTab(
            key: ValueKey('all-$_tabKey-0'),
            loader: (p) =>
                ref.read(salidasRepositoryProvider).getSalidas(page: p),
          ),
          _SalidasTab(
            key: ValueKey('prox-$_tabKey-1'),
            loader: (p) => ref
                .read(salidasRepositoryProvider)
                .getSalidas(page: p, estado: 'PLANIFICADA'),
          ),
          _MisSalidasTab(key: ValueKey('mis-$_tabKey-2')),
        ],
      ),
    );
  }

  void _showSalidaForm(BuildContext context, [SalidaDetalle? salida]) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => SalidaFormSheet(
        salida: salida,
        onSaved: () => setState(() => _tabKey++),
      ),
    );
  }
}

class _SalidasTab extends StatelessWidget {
  const _SalidasTab({required this.loader, super.key});
  final PageLoader<Salida> loader;

  @override
  Widget build(BuildContext context) {
    return AppPagedList<Salida>(
      loader: loader,
      emptyMessage: 'Sin salidas',
      itemBuilder: (_, salida, _) => _SalidaListItem(salida: salida),
    );
  }
}

class _SalidaListItem extends StatelessWidget {
  const _SalidaListItem({required this.salida});
  final Salida salida;

  @override
  Widget build(BuildContext context) {
    final status = SalidaStatusX.fromString(salida.estado);
    final df = DateFormat('dd MMM yyyy', 'es');

    return AppCard(
      onTap: () => context.push('/salidas/${salida.id}'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(salida.nombre,
                    style: AppTextStyles.bodyLarge
                        .copyWith(fontWeight: FontWeight.w600)),
              ),
              AppStatusBadge(status: status),
            ],
          ),
          const SizedBox(height: 6),
          if (salida.montanaNombre != null)
            Row(children: [
              const Icon(Icons.landscape_outlined,
                  size: 14, color: AppColors.mutedFg),
              const SizedBox(width: 4),
              Text(salida.montanaNombre!,
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg)),
            ]),
          if (salida.fechaInicio != null) ...[
            const SizedBox(height: 4),
            Row(children: [
              const Icon(Icons.calendar_today_outlined,
                  size: 14, color: AppColors.mutedFg),
              const SizedBox(width: 4),
              Text(df.format(salida.fechaInicio!),
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg)),
            ]),
          ],
          if (salida.capacidadMaxima != null) ...[
            const SizedBox(height: 4),
            Row(children: [
              const Icon(Icons.people_outline,
                  size: 14, color: AppColors.mutedFg),
              const SizedBox(width: 4),
              Text(
                  '${salida.totalInscritos ?? 0} / ${salida.capacidadMaxima}',
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg)),
            ]),
          ],
        ],
      ),
    );
  }
}

// ── Tab "Mis Salidas" — historial de participación (Kipu) ─────────────────

class _MisSalidasTab extends ConsumerWidget {
  const _MisSalidasTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authNotifierProvider).asData?.value;
    if (auth is! AuthAuthenticated) {
      return const AppEmptyState(message: 'Sesión no disponible');
    }
    final socioId = auth.user.socioId;
    final async = ref.watch(historialSocioProvider(socioId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
        message: 'Error al cargar tus salidas',
        description: e.toString(),
        actionLabel: 'Reintentar',
        onAction: () => ref.invalidate(historialSocioProvider(socioId)),
      ),
      data: (h) => RefreshIndicator(
        onRefresh: () async =>
            ref.invalidate(historialSocioProvider(socioId)),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(children: [
              Expanded(
                child: _StatCard(
                    value: '${h.totalParticipaciones}',
                    label: 'Participaciones'),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _StatCard(
                    value: '${h.totalCumbresLogradas}', label: 'Cumbres'),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _StatCard(
                    value: '${h.vecesJefeSalida}', label: 'Veces jefe'),
              ),
            ]),
            const SizedBox(height: 16),
            if (h.historial.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 48),
                child: AppEmptyState(
                    message: 'Aún no tienes salidas registradas'),
              )
            else
              ...h.historial.map((item) => _HistorialItem(item: item)),
          ],
        ),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.value, required this.label});
  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        children: [
          Text(value,
              style: const TextStyle(
                  color: AppColors.foreground,
                  fontSize: 22,
                  fontWeight: FontWeight.w700)),
          const SizedBox(height: 2),
          Text(label,
              style: AppTextStyles.bodySmall
                  .copyWith(color: AppColors.mutedFg)),
        ],
      ),
    );
  }
}

class _HistorialItem extends StatelessWidget {
  const _HistorialItem({required this.item});
  final SalidaHistorialItem item;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd MMM yyyy', 'es');
    return AppCard(
      onTap: () => context.push('/salidas/${item.salidaId}'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (item.esJefeSalida) ...[
                const Icon(Icons.workspace_premium_outlined,
                    size: 14, color: AppColors.salidaPlanificada),
                const SizedBox(width: 4),
              ],
              Expanded(
                child: Text(item.salidaNombre,
                    style: AppTextStyles.bodyLarge
                        .copyWith(fontWeight: FontWeight.w600)),
              ),
              AppStatusBadge(
                  status: SalidaStatusX.fromString(item.estadoSalida)),
            ],
          ),
          const SizedBox(height: 6),
          if (item.fecha != null)
            Row(children: [
              const Icon(Icons.calendar_today_outlined,
                  size: 14, color: AppColors.mutedFg),
              const SizedBox(width: 4),
              Text(df.format(item.fecha!),
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg)),
            ]),
          if (item.mountainNombre != null) ...[
            const SizedBox(height: 4),
            Row(children: [
              const Icon(Icons.landscape_outlined,
                  size: 14, color: AppColors.mutedFg),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  item.mountainAltitud != null
                      ? '${item.mountainNombre} · ${item.mountainAltitud} m'
                      : item.mountainNombre!,
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg),
                ),
              ),
            ]),
          ],
        ],
      ),
    );
  }
}

// ── Bottom sheet — Crear / Editar salida ──────────────────────────────────

const _kCategorias = <({String value, String label})>[
  (value: 'ALPINISMO', label: 'Alpinismo'),
  (value: 'CICLISMO', label: 'Ciclismo'),
  (value: 'ESCALADA', label: 'Escalada'),
  (value: 'TREKKING', label: 'Trekking'),
];

class SalidaFormSheet extends ConsumerStatefulWidget {
  const SalidaFormSheet({required this.onSaved, this.salida, super.key});
  final SalidaDetalle? salida;
  final VoidCallback onSaved;

  @override
  ConsumerState<SalidaFormSheet> createState() => _SalidaFormSheetState();
}

class _SalidaFormSheetState extends ConsumerState<SalidaFormSheet> {
  final _nombre = TextEditingController();
  final _capacidad = TextEditingController();

  String? _categoria;
  Montana? _montana;
  Ruta? _ruta;
  bool _nombreManual = false;

  String? _publicoObjetivoId;
  String? _formatoSalidaId;
  String? _nivelMinimoId;

  DateTime? _fechaInicio;
  DateTime? _fechaFin;
  TimeOfDay _horaEncuentro = const TimeOfDay(hour: 6, minute: 0);
  TimeOfDay? _horaRegreso;
  bool _multiDia = false;

  bool _loading = false;
  String? _error;

  static final _df = DateFormat('dd/MM/yyyy', 'es');

  @override
  void initState() {
    super.initState();
    final s = widget.salida;
    if (s != null) _initFromSalida(s);
  }

  void _initFromSalida(SalidaDetalle s) {
    _nombre.text = s.nombre;
    _nombreManual = true;
    _capacidad.text = s.capacidadMaxima?.toString() ?? '';
    _categoria = s.tipoActividad;
    _publicoObjetivoId = s.publicoObjetivoId;
    _formatoSalidaId = s.formatoSalidaId;
    _nivelMinimoId = s.nivelMinimoRequeridoId;
    _fechaInicio = s.fechaInicio;
    _fechaFin = s.fechaFin;
    _multiDia = s.fechaInicio != null &&
        s.fechaFin != null &&
        s.fechaInicio != s.fechaFin;
    _horaEncuentro = _parseTime(s.horaEncuentro) ?? _horaEncuentro;
    _horaRegreso = _parseTime(s.horaEstimadaRegreso);
    if (s.rutaId != null) _reconstruirRuta(s.rutaId!);
  }

  /// En modo edición: deriva categoría y montaña a partir de la ruta guardada.
  Future<void> _reconstruirRuta(int rutaId) async {
    try {
      final ruta =
          await ref.read(rutasRepositoryProvider).getRutaDetail(rutaId);
      Montana? montana;
      if (ruta.montanaId != null) {
        montana = await ref
            .read(montanasRepositoryProvider)
            .getMontanaDetail(ruta.montanaId!);
      }
      if (!mounted) return;
      setState(() {
        _categoria = ruta.tipoActividad ?? _categoria;
        _montana = montana;
        _ruta = ruta;
      });
    } catch (_) {
      // Si falla, el usuario puede re-seleccionar manualmente.
    }
  }

  TimeOfDay? _parseTime(String? hhmm) {
    if (hhmm == null || hhmm.isEmpty) return null;
    final parts = hhmm.split(':');
    if (parts.length < 2) return null;
    final h = int.tryParse(parts[0]);
    final m = int.tryParse(parts[1]);
    if (h == null || m == null) return null;
    return TimeOfDay(hour: h, minute: m);
  }

  @override
  void dispose() {
    _nombre.dispose();
    _capacidad.dispose();
    super.dispose();
  }

  String _fmtTime(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:'
      '${t.minute.toString().padLeft(2, '0')}';

  String _fmtDate(DateTime d) => d.toIso8601String().substring(0, 10);

  String? get _categoriaLabel {
    for (final c in _kCategorias) {
      if (c.value == _categoria) return c.label;
    }
    return null;
  }

  void _applyAutoNombre() {
    if (_nombreManual) return;
    if (_ruta == null) {
      _nombre.text = '';
      return;
    }
    if (_categoria == 'ALPINISMO' && _montana != null) {
      _nombre.text = '${_montana!.nombre} — ${_ruta!.nombre}';
    } else {
      _nombre.text = _ruta!.nombre;
    }
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _pickCategoria() async {
    final picked = await _pickOption<({String value, String label})>(
      title: 'Categoría',
      options: _kCategorias,
      labelOf: (c) => c.label,
      isSelected: (c) => c.value == _categoria,
    );
    if (picked == null || picked.value == _categoria) return;
    setState(() {
      _categoria = picked.value;
      _montana = null;
      _ruta = null;
      _nombreManual = false;
      _nombre.text = '';
    });
  }

  Future<void> _pickMontana(AsyncValue<List<Montana>> montanasAsync) async {
    final montanas = montanasAsync.asData?.value;
    if (montanas == null) {
      _toast('Cargando montañas, intenta de nuevo');
      return;
    }
    final picked = await _pickOption<Montana>(
      title: 'Montaña',
      options: montanas,
      labelOf: (m) => m.nombre,
      subtitleOf: (m) =>
          m.altitud != null ? '${m.altitud!.toStringAsFixed(0)} msnm' : null,
      isSelected: (m) => m.id == _montana?.id,
      searchable: true,
    );
    if (picked == null) return;
    setState(() {
      _montana = picked;
      _ruta = null;
      _nombreManual = false;
      _nombre.text = '';
    });
  }

  Future<void> _pickRuta(AsyncValue<List<Ruta>>? rutasAsync) async {
    if (rutasAsync == null) return;
    final rutas = rutasAsync.asData?.value;
    if (rutas == null) {
      _toast('Cargando rutas, intenta de nuevo');
      return;
    }
    if (rutas.isEmpty) {
      _toast('No hay rutas aprobadas para esta categoría');
      return;
    }
    final picked = await _pickOption<Ruta>(
      title: 'Ruta',
      options: rutas,
      labelOf: (r) => r.nombre,
      subtitleOf: (r) => r.dificultadResumen,
      isSelected: (r) => r.id == _ruta?.id,
      searchable: rutas.length > 7,
    );
    if (picked == null) return;
    setState(() {
      _ruta = picked;
      _nombreManual = false;
      _applyAutoNombre();
    });
  }

  Future<T?> _pickOption<T>({
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
      builder: (_) => _OptionPickerSheet<T>(
        title: title,
        options: options,
        labelOf: labelOf,
        subtitleOf: subtitleOf,
        isSelected: isSelected,
        searchable: searchable,
      ),
    );
  }

  Future<void> _pickDate(bool isStart) async {
    final initial = isStart
        ? (_fechaInicio ?? DateTime.now())
        : (_fechaFin ?? _fechaInicio ?? DateTime.now());
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2020),
      lastDate: DateTime(2035),
      builder: (ctx, child) => Theme(
        data: ThemeData.dark().copyWith(
          colorScheme: const ColorScheme.dark(primary: AppColors.primary),
        ),
        child: child!,
      ),
    );
    if (picked == null) return;
    setState(() {
      if (isStart) {
        _fechaInicio = picked;
        if (!_multiDia) _fechaFin = picked;
        if (_fechaFin != null && _fechaFin!.isBefore(picked)) {
          _fechaFin = picked;
        }
      } else {
        _fechaFin = picked;
      }
    });
  }

  Future<void> _pickTime(bool encuentro) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: encuentro
          ? _horaEncuentro
          : (_horaRegreso ?? const TimeOfDay(hour: 12, minute: 0)),
      builder: (ctx, child) => Theme(
        data: ThemeData.dark().copyWith(
          colorScheme: const ColorScheme.dark(primary: AppColors.primary),
        ),
        child: child!,
      ),
    );
    if (picked == null) return;
    setState(() {
      if (encuentro) {
        _horaEncuentro = picked;
      } else {
        _horaRegreso = picked;
      }
    });
  }

  Future<bool?> _confirmarSolapamiento(List<Solapamiento> items) {
    return showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: AppColors.background,
        title: const Text('Solapamiento de fechas'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              items.length == 1
                  ? 'Las fechas coinciden con otra salida activa:'
                  : 'Las fechas coinciden con ${items.length} salidas '
                      'activas:',
              style:
                  AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
            ),
            const SizedBox(height: 10),
            ...items.map((s) => Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.warning_amber_outlined,
                          size: 14, color: AppColors.salidaPlanificada),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          '${s.nombre} · '
                          '${s.fechaInicio == s.fechaFin ? s.fechaInicio : '${s.fechaInicio} → ${s.fechaFin}'}',
                          style: AppTextStyles.bodySmall,
                        ),
                      ),
                    ],
                  ),
                )),
            const SizedBox(height: 8),
            const Text('¿Continuar de todos modos?',
                style: TextStyle(color: AppColors.foreground)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Volver'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Sí, continuar'),
          ),
        ],
      ),
    );
  }

  Future<void> _save() async {
    if (_categoria == null) {
      setState(() => _error = 'Selecciona una categoría');
      return;
    }
    if (_nombre.text.trim().isEmpty) {
      setState(() => _error = 'El nombre es obligatorio');
      return;
    }
    if (_fechaInicio == null) {
      setState(() => _error = 'La fecha es obligatoria');
      return;
    }
    if (_multiDia && _fechaFin == null) {
      setState(() => _error = 'La fecha de fin es obligatoria');
      return;
    }

    final fi = _fmtDate(_fechaInicio!);
    final ff = _multiDia ? _fmtDate(_fechaFin!) : fi;

    final data = <String, dynamic>{
      'nombre': _nombre.text.trim(),
      'fechaInicio': fi,
      'fechaFin': ff,
      'horaEncuentroClub': _fmtTime(_horaEncuentro),
      'tipoActividad': _categoria,
      if (_horaRegreso != null)
        'horaEstimadaRegresoClub': _fmtTime(_horaRegreso!),
      if (_ruta != null) 'rutaId': _ruta!.id,
      if (_publicoObjetivoId != null) 'publicoObjetivoId': _publicoObjetivoId,
      if (_formatoSalidaId != null) 'formatoSalidaId': _formatoSalidaId,
      if (_nivelMinimoId != null) 'nivelMinimoRequeridoId': _nivelMinimoId,
      if (_capacidad.text.trim().isNotEmpty)
        'capacidadMaxima': int.tryParse(_capacidad.text.trim()),
    };

    setState(() {
      _loading = true;
      _error = null;
    });

    final repo = ref.read(salidasRepositoryProvider);

    // Verificación de solapamiento de fechas (no bloquea si la API falla).
    try {
      final solapadas = await repo.verificarSolapamiento(
        fechaInicio: fi,
        fechaFin: ff,
        excludeId: widget.salida?.id,
      );
      if (solapadas.isNotEmpty && mounted) {
        final continuar = await _confirmarSolapamiento(solapadas);
        if (continuar != true) {
          setState(() => _loading = false);
          return;
        }
      }
    } catch (_) {
      // Ignorar — no impedir el guardado.
    }

    try {
      if (widget.salida == null) {
        await repo.crearSalida(data);
      } else {
        await repo.editarSalida(widget.salida!.id, data);
      }
      widget.onSaved();
      if (mounted) Navigator.pop(context);
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  String? _nombreFromLookup(List<SalidaLookupItem> list, String? id) {
    if (id == null) return null;
    for (final i in list) {
      if (i.id == id) return i.nombre;
    }
    return null;
  }

  String? _nivelNombre(List<Clasificacion> list) {
    if (_nivelMinimoId == null) return null;
    for (final c in list) {
      if (c.id == _nivelMinimoId) return c.nombre;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final isEdit = widget.salida != null;
    final lookupsAsync = ref.watch(salidaLookupsProvider);
    final clasifAsync = ref.watch(clasificacionesProvider);
    final montanasAsync = ref.watch(allMontanasProvider);

    AsyncValue<List<Ruta>>? rutasAsync;
    if (_categoria == 'ALPINISMO') {
      if (_montana != null) {
        rutasAsync = ref.watch(rutasByMontanaProvider(_montana!.id));
      }
    } else if (_categoria != null) {
      rutasAsync = ref.watch(rutasByActividadProvider(_categoria!));
    }

    final publicos = lookupsAsync.asData?.value.publicosObjetivo ??
        const <SalidaLookupItem>[];
    final formatos = lookupsAsync.asData?.value.formatosSalida ??
        const <SalidaLookupItem>[];
    final clasif = clasifAsync.asData?.value ?? const <Clasificacion>[];

    final rutaEnabled = _categoria != null &&
        (_categoria != 'ALPINISMO' || _montana != null);

    String rutaPlaceholder() {
      if (_categoria == null) return 'Selecciona una categoría';
      if (_categoria == 'ALPINISMO' && _montana == null) {
        return 'Primero selecciona una montaña';
      }
      if (rutasAsync == null || rutasAsync.isLoading) {
        return 'Cargando rutas...';
      }
      if ((rutasAsync.asData?.value ?? const []).isEmpty) {
        return 'No hay rutas aprobadas';
      }
      return 'Seleccionar ruta (opcional)';
    }

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
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(isEdit ? 'Editar salida' : 'Nueva salida',
                style: AppTextStyles.titleMedium),
            const SizedBox(height: 20),

            // 1. Categoría
            _SelectField(
              label: 'Categoría *',
              value: _categoriaLabel,
              placeholder: 'Seleccionar categoría',
              onTap: _pickCategoria,
            ),

            // 2. Montaña (solo Alpinismo)
            if (_categoria == 'ALPINISMO') ...[
              const SizedBox(height: 12),
              _SelectField(
                label: 'Montaña *',
                value: _montana?.nombre,
                placeholder: 'Buscar montaña',
                onTap: () => _pickMontana(montanasAsync),
              ),
            ],

            // 3. Ruta
            if (_categoria != null) ...[
              const SizedBox(height: 12),
              _SelectField(
                label: 'Ruta',
                value: _ruta?.nombre,
                placeholder: rutaPlaceholder(),
                onTap: rutaEnabled ? () => _pickRuta(rutasAsync) : null,
              ),
            ],

            const SizedBox(height: 12),
            // 4. Nombre
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Nombre *',
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
                const SizedBox(height: 4),
                AppInput(
                  controller: _nombre,
                  hint: 'Nombre de la salida',
                  onChanged: (_) => _nombreManual = true,
                ),
              ],
            ),

            const SizedBox(height: 12),
            // 5. Público objetivo y formato
            _SelectField(
              label: 'Público objetivo',
              value: _nombreFromLookup(publicos, _publicoObjetivoId),
              placeholder: 'Seleccionar',
              onTap: () async {
                final picked = await _pickOption<SalidaLookupItem>(
                  title: 'Público objetivo',
                  options: publicos,
                  labelOf: (p) => p.nombre,
                  isSelected: (p) => p.id == _publicoObjetivoId,
                );
                if (picked != null) {
                  setState(() => _publicoObjetivoId = picked.id);
                }
              },
            ),
            const SizedBox(height: 12),
            _SelectField(
              label: 'Formato',
              value: _nombreFromLookup(formatos, _formatoSalidaId),
              placeholder: 'Seleccionar',
              onTap: () async {
                final picked = await _pickOption<SalidaLookupItem>(
                  title: 'Formato',
                  options: formatos,
                  labelOf: (f) => f.nombre,
                  isSelected: (f) => f.id == _formatoSalidaId,
                );
                if (picked != null) {
                  setState(() => _formatoSalidaId = picked.id);
                }
              },
            ),

            const SizedBox(height: 12),
            // 6. Fechas
            if (!_multiDia)
              _DateField(
                label: 'Fecha *',
                value: _fechaInicio,
                formatter: _df,
                onTap: () => _pickDate(true),
              )
            else
              Row(children: [
                Expanded(
                  child: _DateField(
                    label: 'Fecha inicio *',
                    value: _fechaInicio,
                    formatter: _df,
                    onTap: () => _pickDate(true),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _DateField(
                    label: 'Fecha fin *',
                    value: _fechaFin,
                    formatter: _df,
                    onTap: () => _pickDate(false),
                  ),
                ),
              ]),
            const SizedBox(height: 8),
            Row(children: [
              SizedBox(
                height: 24,
                width: 24,
                child: Checkbox(
                  value: _multiDia,
                  activeColor: AppColors.primary,
                  onChanged: (v) => setState(() {
                    _multiDia = v ?? false;
                    if (!_multiDia) _fechaFin = _fechaInicio;
                  }),
                ),
              ),
              const SizedBox(width: 8),
              GestureDetector(
                onTap: () => setState(() {
                  _multiDia = !_multiDia;
                  if (!_multiDia) _fechaFin = _fechaInicio;
                }),
                child: Text('Salida de varios días',
                    style: AppTextStyles.bodySmall),
              ),
            ]),

            const SizedBox(height: 12),
            // Horas
            Row(children: [
              Expanded(
                child: _TimeField(
                  label: 'Hora encuentro *',
                  value: _horaEncuentro,
                  onTap: () => _pickTime(true),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _TimeField(
                  label: 'Hora regreso est.',
                  value: _horaRegreso,
                  onTap: () => _pickTime(false),
                ),
              ),
            ]),

            const SizedBox(height: 12),
            // 7. Nivel mínimo y capacidad
            _SelectField(
              label: 'Nivel mínimo',
              value: _nivelNombre(clasif),
              placeholder: 'Sin restricción',
              onTap: () async {
                final picked = await _pickOption<Clasificacion>(
                  title: 'Nivel mínimo',
                  options: clasif,
                  labelOf: (c) => c.nombre,
                  isSelected: (c) => c.id == _nivelMinimoId,
                );
                if (picked != null) {
                  setState(() => _nivelMinimoId = picked.id);
                }
              },
            ),
            const SizedBox(height: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Capacidad',
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
                const SizedBox(height: 4),
                AppInput(
                  controller: _capacidad,
                  hint: 'Sin límite',
                  keyboardType: TextInputType.number,
                ),
              ],
            ),

            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!,
                  style: const TextStyle(
                      color: AppColors.destructive, fontSize: 13)),
            ],
            const SizedBox(height: 24),
            AppButton(
              label: isEdit ? 'Guardar cambios' : 'Crear salida',
              fullWidth: true,
              loading: _loading,
              onPressed: _save,
            ),
          ],
        ),
      ),
    );
  }
}

class _SelectField extends StatelessWidget {
  const _SelectField({
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
    final enabled = onTap != null;
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
              color:
                  AppColors.secondary.withValues(alpha: enabled ? 1 : 0.5),
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
                      fontSize: 14,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Icon(Icons.keyboard_arrow_down,
                    size: 18,
                    color: enabled ? AppColors.mutedFg : AppColors.border),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _DateField extends StatelessWidget {
  const _DateField({
    required this.label,
    required this.value,
    required this.formatter,
    required this.onTap,
  });

  final String label;
  final DateTime? value;
  final DateFormat formatter;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style:
                  AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
          const SizedBox(height: 4),
          Container(
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
                    value != null ? formatter.format(value!) : 'Seleccionar',
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
        ],
      ),
    );
  }
}

class _TimeField extends StatelessWidget {
  const _TimeField({
    required this.label,
    required this.value,
    required this.onTap,
  });

  final String label;
  final TimeOfDay? value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final txt = value != null
        ? '${value!.hour.toString().padLeft(2, '0')}:'
            '${value!.minute.toString().padLeft(2, '0')}'
        : 'Seleccionar';
    return GestureDetector(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style:
                  AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
          const SizedBox(height: 4),
          Container(
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
                  child: Text(txt,
                      style: TextStyle(
                        color: value != null
                            ? AppColors.foreground
                            : AppColors.mutedFg,
                        fontSize: 14,
                      )),
                ),
                const Icon(Icons.access_time,
                    size: 16, color: AppColors.mutedFg),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── Picker genérico con búsqueda opcional ─────────────────────────────────

class _OptionPickerSheet<T> extends StatefulWidget {
  const _OptionPickerSheet({
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
  State<_OptionPickerSheet<T>> createState() => _OptionPickerSheetState<T>();
}

class _OptionPickerSheetState<T> extends State<_OptionPickerSheet<T>> {
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
            .where((o) => widget.labelOf(o).toLowerCase().contains(_query))
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
                  borderRadius: BorderRadius.circular(2),
                ),
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
                          style: TextStyle(color: AppColors.mutedFg)),
                    )
                  : ListView.builder(
                      itemCount: filtered.length,
                      itemBuilder: (_, i) {
                        final o = filtered[i];
                        final sel = widget.isSelected?.call(o) ?? false;
                        final sub = widget.subtitleOf?.call(o);
                        return ListTile(
                          title: Text(
                            widget.labelOf(o),
                            style: TextStyle(
                              color: sel
                                  ? AppColors.primary
                                  : AppColors.foreground,
                            ),
                          ),
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
