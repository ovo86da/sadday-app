import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/app_exception.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../rutas/domain/models/ruta_model.dart';
import '../../domain/models/recomendacion_model.dart';
import '../providers/planificador_provider.dart';

class PlanificadorScreen extends ConsumerStatefulWidget {
  const PlanificadorScreen({this.initialRutaId, super.key});

  /// Ruta a preseleccionar al abrir — usada al llegar desde una salida.
  final int? initialRutaId;

  @override
  ConsumerState<PlanificadorScreen> createState() =>
      _PlanificadorScreenState();
}

class _PlanificadorScreenState extends ConsumerState<PlanificadorScreen> {
  Ruta? _selectedRuta;
  bool _preseleccionAplicada = false;

  @override
  Widget build(BuildContext context) {
    final rutas = ref.watch(rutasParaPlanificadorProvider);

    if (!_preseleccionAplicada && widget.initialRutaId != null) {
      final lista = rutas.asData?.value;
      if (lista != null) {
        _preseleccionAplicada = true;
        Ruta? match;
        for (final r in lista) {
          if (r.id == widget.initialRutaId) {
            match = r;
            break;
          }
        }
        if (match != null) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) setState(() => _selectedRuta = match);
          });
        }
      }
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Planificador'), centerTitle: false),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Consulta estadísticas históricas y datos técnicos de una ruta '
            'antes de planificar tu salida.',
            style:
                AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
          ),
          const SizedBox(height: 16),
          rutas.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => AppEmptyState(
                message: 'Error al cargar rutas',
                description: unwrapDio(e).toString()),
            data: (lista) => _RutaSelector(
              rutas: lista,
              selected: _selectedRuta,
              onChanged: (r) => setState(() => _selectedRuta = r),
            ),
          ),
          const SizedBox(height: 16),
          if (_selectedRuta != null)
            _Recomendacion(rutaId: _selectedRuta!.id),
        ],
      ),
    );
  }
}

class _RutaSelector extends StatelessWidget {
  const _RutaSelector({
    required this.rutas,
    required this.selected,
    required this.onChanged,
  });
  final List<Ruta> rutas;
  final Ruta? selected;
  final ValueChanged<Ruta> onChanged;

  String _label(Ruta r) =>
      r.montanaNombre != null ? '${r.nombre} · ${r.montanaNombre}' : r.nombre;

  @override
  Widget build(BuildContext context) {
    if (rutas.isEmpty) {
      return const AppEmptyState(message: 'No hay rutas disponibles');
    }
    return AppCard(
      onTap: () async {
        final picked = await showModalBottomSheet<Ruta>(
          context: context,
          backgroundColor: AppColors.sidebar,
          isScrollControlled: true,
          builder: (_) => _RutaSearchSheet(rutas: rutas),
        );
        if (picked != null) onChanged(picked);
      },
      child: Row(
        children: [
          const Icon(Icons.route_outlined,
              color: AppColors.primary, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Ruta',
                    style: AppTextStyles.labelSmall
                        .copyWith(color: AppColors.mutedFg)),
                Text(
                  selected != null
                      ? _label(selected!)
                      : 'Elige una ruta',
                  style: AppTextStyles.bodyMedium.copyWith(
                      fontWeight: FontWeight.w600,
                      color: selected != null
                          ? AppColors.foreground
                          : AppColors.mutedFg),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          const Icon(Icons.unfold_more, color: AppColors.mutedFg, size: 20),
        ],
      ),
    );
  }
}

/// Bottom sheet con búsqueda para elegir una ruta entre muchas.
class _RutaSearchSheet extends StatefulWidget {
  const _RutaSearchSheet({required this.rutas});
  final List<Ruta> rutas;

  @override
  State<_RutaSearchSheet> createState() => _RutaSearchSheetState();
}

class _RutaSearchSheetState extends State<_RutaSearchSheet> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final q = _query.trim().toLowerCase();
    final filtradas = q.isEmpty
        ? widget.rutas
        : widget.rutas.where((r) {
            final hay = '${r.nombre} ${r.montanaNombre ?? ''}'.toLowerCase();
            return hay.contains(q);
          }).toList();

    return Padding(
      padding: EdgeInsets.only(
          bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.7,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: TextField(
                autofocus: true,
                onChanged: (v) => setState(() => _query = v),
                decoration: const InputDecoration(
                  hintText: 'Buscar ruta…',
                  prefixIcon: Icon(Icons.search),
                ),
              ),
            ),
            Expanded(
              child: filtradas.isEmpty
                  ? const AppEmptyState(message: 'Sin coincidencias')
                  : ListView.builder(
                      itemCount: filtradas.length,
                      itemBuilder: (_, i) {
                        final r = filtradas[i];
                        return ListTile(
                          leading: const Icon(Icons.route_outlined,
                              color: AppColors.primary, size: 20),
                          title: Text(r.nombre,
                              style: AppTextStyles.bodyMedium),
                          subtitle: r.montanaNombre != null
                              ? Text(r.montanaNombre!,
                                  style: AppTextStyles.labelSmall.copyWith(
                                      color: AppColors.mutedFg))
                              : null,
                          onTap: () => Navigator.pop(context, r),
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

class _Recomendacion extends ConsumerWidget {
  const _Recomendacion({required this.rutaId});
  final int rutaId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(recomendacionProvider(rutaId));
    return async.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) => AppEmptyState(
          message: 'Error al cargar recomendación',
          description: unwrapDio(e).toString()),
      data: (r) => _RecomendacionBody(r: r),
    );
  }
}

class _RecomendacionBody extends StatelessWidget {
  const _RecomendacionBody({required this.r});
  final Recomendacion r;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // ── Datos de la ruta ──────────────────────────────────────────────
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(r.rutaNombre,
                  style: AppTextStyles.titleMedium
                      .copyWith(fontWeight: FontWeight.bold)),
              const SizedBox(height: 6),
              if (r.montanaNombre != null)
                _InfoRow(Icons.landscape_outlined, r.montanaNombre!),
              if (r.tipoActividad != null)
                _InfoRow(Icons.category_outlined, r.tipoActividad!),
              if (r.sectorZona != null)
                _InfoRow(Icons.place_outlined, r.sectorZona!),
              if (r.requierePermisos)
                _InfoRow(Icons.assignment_outlined,
                    'Requiere permisos de acceso'),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // ── Datos técnicos ────────────────────────────────────────────────
        if (r.datosTecnicos.isNotEmpty) ...[
          _SectionTitle('Datos técnicos'),
          AppCard(
            child: Column(
              children: [
                for (final d in r.datosTecnicos)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 5),
                    child: Row(
                      children: [
                        SizedBox(
                          width: 150,
                          child: Text(d.label,
                              style: AppTextStyles.bodySmall
                                  .copyWith(color: AppColors.mutedFg)),
                        ),
                        Expanded(
                          child: Text(d.value,
                              style: AppTextStyles.bodyMedium),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
        ],

        // ── Estadísticas históricas ───────────────────────────────────────
        _SectionTitle('Estadísticas históricas'),
        AppCard(
          child: r.datosInsuficientes
              ? Row(
                  children: [
                    const Icon(Icons.info_outline,
                        color: AppColors.salidaPlanificada, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Datos insuficientes — solo '
                        '${r.totalSalidasPrevias} salida'
                        '${r.totalSalidasPrevias != 1 ? 's' : ''} previa'
                        '${r.totalSalidasPrevias != 1 ? 's' : ''} con informe.',
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg),
                      ),
                    ),
                  ],
                )
              : Column(
                  children: [
                    _StatRow(
                        icon: Icons.bar_chart,
                        label: 'Salidas previas con informe',
                        value: '${r.totalSalidasPrevias}'),
                    _StatRow(
                        icon: Icons.check_circle_outline,
                        label: 'Tasa de éxito (cumbre)',
                        value: _pct(r.tasaExitoPct)),
                    _StatRow(
                        icon: Icons.access_time,
                        label: 'Hora promedio de salida',
                        value: r.horaSalidaPromedioClub ?? '—'),
                    _StatRow(
                        icon: Icons.directions_bus_outlined,
                        label: 'Alquiló transporte',
                        value: _pct(r.pctAlquiloTransporte)),
                    _StatRow(
                        icon: Icons.payments_outlined,
                        label: 'Costo transporte promedio',
                        value: _money(r.costoPromedioTransporte)),
                    _StatRow(
                        icon: Icons.hiking_outlined,
                        label: 'Contrató guía',
                        value: _pct(r.pctContratoGuia)),
                    _StatRow(
                        icon: Icons.payments_outlined,
                        label: 'Costo guía promedio',
                        value: _money(r.costoPromedioGuia)),
                    _StatRow(
                        icon: Icons.account_balance_wallet_outlined,
                        label: 'Presupuesto total promedio',
                        value: _money(r.costoTotalPromedio),
                        highlight: true),
                  ],
                ),
        ),
      ],
    );
  }

  static String _pct(double? v) =>
      v == null ? '—' : '${v.toStringAsFixed(0)}%';
  static String _money(double? v) =>
      v == null ? '—' : '\$${v.toStringAsFixed(2)}';
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(text,
            style: AppTextStyles.titleMedium
                .copyWith(fontWeight: FontWeight.w700)),
      );
}

class _InfoRow extends StatelessWidget {
  const _InfoRow(this.icon, this.label);
  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 4),
        child: Row(
          children: [
            Icon(icon, size: 15, color: AppColors.mutedFg),
            const SizedBox(width: 8),
            Expanded(
                child: Text(label, style: AppTextStyles.bodySmall)),
          ],
        ),
      );
}

class _StatRow extends StatelessWidget {
  const _StatRow({
    required this.icon,
    required this.label,
    required this.value,
    this.highlight = false,
  });
  final IconData icon;
  final String label;
  final String value;
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon,
              size: 16,
              color: highlight ? AppColors.primary : AppColors.mutedFg),
          const SizedBox(width: 10),
          Expanded(
            child: Text(label,
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
          ),
          Text(value,
              style: AppTextStyles.bodyMedium.copyWith(
                fontWeight: FontWeight.w700,
                color: highlight ? AppColors.primary : AppColors.foreground,
              )),
        ],
      ),
    );
  }
}
