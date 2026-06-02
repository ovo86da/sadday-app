import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../../core/widgets/app_status_badge.dart';
import '../../../../core/widgets/jefe_salida_banner.dart';
import '../../../../core/widgets/nivel_tecnico_banner.dart';
import '../../domain/models/dashboard_models.dart';
import '../providers/dashboard_provider.dart';

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});

  @override
  ConsumerState<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends ConsumerState<DashboardScreen> {
  int _meses = 12;

  @override
  Widget build(BuildContext context) {
    final asyncStats = ref.watch(dashboardProvider(_meses));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Dashboard'),
        centerTitle: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(dashboardProvider(_meses)),
          ),
        ],
      ),
      body: asyncStats.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => AppEmptyState(
          message: 'Error al cargar el dashboard',
          error: e,
          icon: Icons.error_outline,
          actionLabel: 'Reintentar',
          onAction: () => ref.invalidate(dashboardProvider(_meses)),
        ),
        data: (stats) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(dashboardProvider(_meses)),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const NivelTecnicoBanner(),
              const SizedBox(height: 16),
              if (_hasJefeContent(stats)) ...[
                JefeSalidaBanner(
                  data: JefeAlertasData(
                    aprobacionesPendientes: stats.aprobacionesPendientes,
                    salidasSinJefe: stats.salidasSinJefe,
                    proximasComoJefe: stats.proximasComoJefe,
                  ),
                ),
                const SizedBox(height: 16),
              ],
              _KpiRow(kpis: stats.kpis),
              const SizedBox(height: 16),
              _SectionTitle('Próximas Salidas'),
              if (stats.proximasSalidas.isEmpty)
                const _EmptyCard(
                    icon: Icons.event_busy_outlined,
                    text: 'No hay salidas planificadas')
              else
                ...stats.proximasSalidas.map((s) => _SalidaCard(s)),
              const SizedBox(height: 16),
              _SectionTitle('Cumpleaños hoy 🎂'),
              AppCard(
                child: stats.cumpleanos.isEmpty
                    ? const _InlineEmpty(
                        icon: Icons.cake_outlined,
                        text: 'Ningún socio cumple años hoy')
                    : Column(
                        children: stats.cumpleanos
                            .map((c) => ListTile(
                                  dense: true,
                                  leading: const Icon(Icons.cake_outlined,
                                      color: AppColors.primary, size: 20),
                                  title: Text(c.nombre,
                                      style: AppTextStyles.bodyMedium),
                                  trailing: Text('${c.edad} años',
                                      style: AppTextStyles.bodySmall.copyWith(
                                          color: AppColors.mutedFg)),
                                ))
                            .toList(),
                      ),
              ),
              const SizedBox(height: 16),
              _SectionTitle('Salidas totales del club'),
              _MesesFilter(
                  selected: _meses,
                  onChanged: (v) => setState(() => _meses = v)),
              const SizedBox(height: 8),
              AppCard(
                child: Column(
                  children: [
                    if (stats.salidasPorMes.isNotEmpty) ...[
                      _BarChartWidget(data: stats.salidasPorMes),
                      const SizedBox(height: 8),
                      const _BarChartLegend(),
                      const SizedBox(height: 12),
                      Divider(color: AppColors.border.withValues(alpha: 0.4)),
                      const SizedBox(height: 8),
                    ] else ...[
                      const _InlineEmpty(
                          icon: Icons.bar_chart_outlined,
                          text: 'No hay salidas registradas en este período'),
                      const SizedBox(height: 8),
                      Divider(color: AppColors.border.withValues(alpha: 0.4)),
                      const SizedBox(height: 8),
                    ],
                    _SalidasSummaryFooter(kpis: stats.kpis),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              if (stats.sociosPorTipo.isNotEmpty) ...[
                _SectionTitle('Distribución de Socios'),
                AppCard(
                    child: _PieChartWidget(data: stats.sociosPorTipo)),
                const SizedBox(height: 16),
              ],
            ],
          ),
        ),
      ),
    );
  }

  bool _hasJefeContent(DashboardStats s) =>
      s.aprobacionesPendientes.isNotEmpty ||
      s.salidasSinJefe.isNotEmpty ||
      s.proximasComoJefe.isNotEmpty;
}

/// Card de estado vacío para una sección del dashboard.
class _EmptyCard extends StatelessWidget {
  const _EmptyCard({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) =>
      AppCard(child: _InlineEmpty(icon: icon, text: text));
}

/// Fila compacta de estado vacío (para usar dentro de un AppCard).
class _InlineEmpty extends StatelessWidget {
  const _InlineEmpty({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Icon(icon, color: AppColors.mutedFg, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(text,
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(text,
            style: AppTextStyles.titleMedium
                .copyWith(fontWeight: FontWeight.w600)),
      );
}

class _MesesFilter extends StatelessWidget {
  const _MesesFilter({required this.selected, required this.onChanged});
  final int selected;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [6, 12, 24].map((m) {
        final isSelected = m == selected;
        return Padding(
          padding: const EdgeInsets.only(right: 8),
          child: FilterChip(
            label: Text('$m meses'),
            selected: isSelected,
            onSelected: (_) => onChanged(m),
            selectedColor: AppColors.primary.withValues(alpha: 0.2),
            checkmarkColor: AppColors.primary,
            labelStyle: TextStyle(
                color: isSelected ? AppColors.primary : AppColors.mutedFg,
                fontSize: 12),
          ),
        );
      }).toList(),
    );
  }
}

class _KpiRow extends StatelessWidget {
  const _KpiRow({required this.kpis});
  final DashboardKpis kpis;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        if (kpis.showSocios) ...[
          Expanded(
              child: _KpiChip(
                  label: 'Socios',
                  value: kpis.totalSocios,
                  icon: Icons.people_outline)),
          const SizedBox(width: 8),
        ],
        Expanded(
            child: _KpiChip(
                label: 'Planificadas',
                value: kpis.salidasPlanificadas,
                icon: Icons.event_outlined,
                color: AppColors.salidaPlanificada)),
        const SizedBox(width: 8),
        Expanded(
            child: _KpiChip(
                label: 'Realizadas',
                value: kpis.salidasRealizadas,
                icon: Icons.check_circle_outline,
                color: AppColors.salidaRealizada)),
      ],
    );
  }
}

class _KpiChip extends StatelessWidget {
  const _KpiChip(
      {required this.label, required this.value, required this.icon, this.color});
  final String label;
  final int value;
  final IconData icon;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.primary;
    return AppCard(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          Icon(icon, color: c, size: 22),
          const SizedBox(height: 4),
          Text('$value',
              style: AppTextStyles.headlineMedium.copyWith(
                  color: c, fontWeight: FontWeight.bold)),
          Text(label,
              style: AppTextStyles.labelSmall.copyWith(
                  color: AppColors.mutedFg),
              textAlign: TextAlign.center),
        ],
      ),
    );
  }
}

class _SalidaCard extends StatelessWidget {
  const _SalidaCard(this.salida);
  final ProximaSalida salida;

  @override
  Widget build(BuildContext context) {
    final status = SalidaStatusX.fromString(salida.estado);
    final df = DateFormat('dd/MM/yyyy');
    return AppCard(
      onTap: () => context.push('/salidas/${salida.id}'),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(salida.nombre,
                    style: AppTextStyles.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
                if (salida.montanaNombre != null)
                  Text(salida.montanaNombre!,
                      style: AppTextStyles.bodySmall
                          .copyWith(color: AppColors.mutedFg)),
                if (salida.fechaInicio != null)
                  Text(df.format(salida.fechaInicio!),
                      style: AppTextStyles.labelSmall
                          .copyWith(color: AppColors.mutedFg)),
              ],
            ),
          ),
          AppStatusBadge(status: status),
        ],
      ),
    );
  }
}

class _BarChartWidget extends StatelessWidget {
  const _BarChartWidget({required this.data});
  final List<SalidaMesPoint> data;

  static const _colorRealizadas = Color(0xFF10b981);
  static const _colorCanceladas = Color(0xFFf43f5e);
  static const _colorEnCurso = Color(0xFF3b82f6);

  @override
  Widget build(BuildContext context) {
    if (data.isEmpty) return const SizedBox(height: 120);
    final groups = data.asMap().entries.map((e) {
      final p = e.value;
      return BarChartGroupData(
        x: e.key,
        barsSpace: 2,
        barRods: [
          _rod(p.realizadas, _colorRealizadas),
          _rod(p.canceladas, _colorCanceladas),
          _rod(p.enCurso, _colorEnCurso),
        ],
      );
    }).toList();

    return SizedBox(
      height: 180,
      child: BarChart(
        BarChartData(
          barGroups: groups,
          groupsSpace: 12,
          gridData: const FlGridData(show: false),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 22,
                getTitlesWidget: (val, _) {
                  final idx = val.toInt();
                  if (idx < 0 || idx >= data.length) return const SizedBox();
                  return Text(data[idx].label,
                      style: const TextStyle(
                          color: AppColors.mutedFg, fontSize: 9));
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  BarChartRodData _rod(int value, Color color) => BarChartRodData(
        toY: value.toDouble(),
        color: color,
        width: 6,
        borderRadius: BorderRadius.circular(2),
      );
}

class _BarChartLegend extends StatelessWidget {
  const _BarChartLegend();

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 16,
      runSpacing: 4,
      alignment: WrapAlignment.center,
      children: const [
        _LegendDot(color: Color(0xFF10b981), label: 'Realizadas'),
        _LegendDot(color: Color(0xFFf43f5e), label: 'Canceladas'),
        _LegendDot(color: Color(0xFF3b82f6), label: 'En curso'),
      ],
    );
  }
}

class _LegendDot extends StatelessWidget {
  const _LegendDot({required this.color, required this.label});
  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Text(label,
            style: const TextStyle(color: AppColors.mutedFg, fontSize: 11)),
      ],
    );
  }
}

class _SalidasSummaryFooter extends StatelessWidget {
  const _SalidasSummaryFooter({required this.kpis});
  final DashboardKpis kpis;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
            child: _MiniKpi(
                value: kpis.totalSalidas,
                label: 'Total',
                color: AppColors.foreground)),
        Expanded(
            child: _MiniKpi(
                value: kpis.salidasRealizadas,
                label: 'Realizadas',
                color: const Color(0xFF10b981))),
        Expanded(
            child: _MiniKpi(
                value: kpis.totalEnCurso,
                label: 'En curso',
                color: const Color(0xFF3b82f6))),
        Expanded(
            child: _MiniKpi(
                value: kpis.totalCanceladas,
                label: 'Canceladas',
                color: const Color(0xFFf43f5e))),
      ],
    );
  }
}

class _MiniKpi extends StatelessWidget {
  const _MiniKpi(
      {required this.value, required this.label, required this.color});
  final int value;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text('$value',
            style: AppTextStyles.titleLarge
                .copyWith(color: color, fontWeight: FontWeight.bold)),
        const SizedBox(height: 2),
        Text(label,
            style: AppTextStyles.labelSmall
                .copyWith(color: AppColors.mutedFg),
            textAlign: TextAlign.center),
      ],
    );
  }
}

class _PieChartWidget extends StatelessWidget {
  const _PieChartWidget({required this.data});
  final List<ChartDataPoint> data;

  static final _colors = [
    AppColors.chart1,
    AppColors.chart2,
    AppColors.chart3,
    AppColors.chart4,
    AppColors.chart5,
  ];

  @override
  Widget build(BuildContext context) {
    if (data.isEmpty) return const SizedBox(height: 120);
    final total = data.fold<double>(0, (s, d) => s + d.value);
    final sections = data.asMap().entries.map((e) {
      final color = _colors[e.key % _colors.length];
      final pct = total > 0 ? (e.value.value / total * 100).round() : 0;
      return PieChartSectionData(
        value: e.value.value,
        color: color,
        title: '$pct%',
        radius: 55,
        titleStyle: const TextStyle(
            color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600),
      );
    }).toList();

    return Row(
      children: [
        SizedBox(
          height: 140,
          width: 140,
          child: PieChart(PieChartData(
            sections: sections,
            centerSpaceRadius: 30,
            sectionsSpace: 2,
          )),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: data.asMap().entries.map((e) {
              final color = _colors[e.key % _colors.length];
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(
                  children: [
                    Container(
                        width: 10,
                        height: 10,
                        decoration: BoxDecoration(
                            color: color, shape: BoxShape.circle)),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(e.value.label,
                          style: const TextStyle(
                              color: AppColors.foreground, fontSize: 12)),
                    ),
                    Text(e.value.value.toInt().toString(),
                        style: const TextStyle(
                            color: AppColors.mutedFg, fontSize: 12)),
                  ],
                ),
              );
            }).toList(),
          ),
        ),
      ],
    );
  }
}
