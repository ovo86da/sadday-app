import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../domain/models/estadisticas_models.dart';
import '../providers/estadisticas_provider.dart';

class EstadisticasScreen extends ConsumerStatefulWidget {
  const EstadisticasScreen({super.key});

  @override
  ConsumerState<EstadisticasScreen> createState() => _EstadisticasScreenState();
}

class _EstadisticasScreenState extends ConsumerState<EstadisticasScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  int _meses = 12;
  int _top = 10;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Estadísticas'),
        centerTitle: false,
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Resumen'),
            Tab(text: 'Rankings'),
            Tab(text: 'Montañas'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _ResumenTab(meses: _meses, onMesesChanged: (m) => setState(() => _meses = m)),
          _RankingsTab(top: _top, onTopChanged: (t) => setState(() => _top = t)),
          _MontanasTab(),
        ],
      ),
    );
  }
}

class _ResumenTab extends ConsumerWidget {
  const _ResumenTab({required this.meses, required this.onMesesChanged});
  final int meses;
  final ValueChanged<int> onMesesChanged;

  static final _colors = [
    AppColors.chart1,
    AppColors.chart2,
    AppColors.chart3,
    AppColors.chart4,
    AppColors.chart5,
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(estadisticasClubProvider(meses));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
        message: 'Error al cargar estadísticas',
        description: e.toString(),
        actionLabel: 'Reintentar',
        onAction: () => ref.invalidate(estadisticasClubProvider(meses)),
      ),
      data: (stats) => RefreshIndicator(
        onRefresh: () async => ref.invalidate(estadisticasClubProvider(meses)),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // KPIs
            GridView.count(
              crossAxisCount: 2,
              crossAxisSpacing: 8,
              mainAxisSpacing: 8,
              childAspectRatio: 2.0,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              children: [
                _KpiCard('Salidas', '${stats.totalSalidas}', Icons.hiking),
                _KpiCard(
                    'Socios activos', '${stats.sociosActivos}', Icons.people),
                _KpiCard('Cumbres', '${stats.cumbresLogradas}',
                    Icons.landscape, AppColors.salidaRealizada),
                _KpiCard(
                    'Tasa éxito',
                    '${stats.tasaExito.toStringAsFixed(1)}%',
                    Icons.trending_up,
                    AppColors.chart3),
              ],
            ),
            const SizedBox(height: 16),

            // Salidas por mes
            Text('Salidas por mes',
                style: AppTextStyles.titleMedium
                    .copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            Row(
              children: [6, 12, 24].map((m) {
                final sel = m == meses;
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: FilterChip(
                    label: Text('$m m'),
                    selected: sel,
                    onSelected: (_) => onMesesChanged(m),
                    selectedColor: AppColors.primary.withValues(alpha: 0.2),
                    labelStyle: TextStyle(
                        color: sel ? AppColors.primary : AppColors.mutedFg,
                        fontSize: 12),
                  ),
                );
              }).toList(),
            ),
            const SizedBox(height: 8),
            if (stats.salidasPorMes.isNotEmpty)
              AppCard(child: _BarChart(data: stats.salidasPorMes)),
            const SizedBox(height: 16),

            // Distribución por nivel
            if (stats.distribucionNiveles.isNotEmpty) ...[
              Text('Distribución por nivel técnico',
                  style: AppTextStyles.titleMedium
                      .copyWith(fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              AppCard(
                  child: _PieWithLegend(
                      data: stats.distribucionNiveles, colors: _colors)),
              const SizedBox(height: 16),
            ],

            // Por actividad
            if (stats.salidasPorActividad.isNotEmpty) ...[
              Text('Salidas por actividad',
                  style: AppTextStyles.titleMedium
                      .copyWith(fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              AppCard(
                  child: _PieWithLegend(
                      data: stats.salidasPorActividad, colors: _colors)),
            ],
          ],
        ),
      ),
    );
  }
}

class _KpiCard extends StatelessWidget {
  const _KpiCard(this.label, this.value, this.icon, [this.color]);
  final String label;
  final String value;
  final IconData icon;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.primary;
    return AppCard(
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          Icon(icon, color: c, size: 22),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(value,
                    style: AppTextStyles.titleLarge
                        .copyWith(color: c, fontWeight: FontWeight.bold)),
                Text(label,
                    style: AppTextStyles.labelSmall
                        .copyWith(color: AppColors.mutedFg),
                    overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _BarChart extends StatelessWidget {
  const _BarChart({required this.data});
  final List<ChartDataPoint> data;

  @override
  Widget build(BuildContext context) {
    final groups = data.asMap().entries.map((e) {
      return BarChartGroupData(x: e.key, barRods: [
        BarChartRodData(
          toY: e.value.value,
          color: AppColors.primary,
          width: 14,
          borderRadius: BorderRadius.circular(3),
        )
      ]);
    }).toList();

    return SizedBox(
      height: 160,
      child: BarChart(BarChartData(
        barGroups: groups,
        gridData: const FlGridData(show: false),
        borderData: FlBorderData(show: false),
        titlesData: FlTitlesData(
          leftTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          topTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
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
      )),
    );
  }
}

class _PieWithLegend extends StatelessWidget {
  const _PieWithLegend({required this.data, required this.colors});
  final List<ChartDataPoint> data;
  final List<Color> colors;

  @override
  Widget build(BuildContext context) {
    final total = data.fold<double>(0, (s, d) => s + d.value);
    final sections = data.asMap().entries.map((e) {
      final color = colors[e.key % colors.length];
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
              sections: sections, centerSpaceRadius: 30, sectionsSpace: 2)),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: data.asMap().entries.map((e) {
              final color = colors[e.key % colors.length];
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(children: [
                  Container(
                      width: 10,
                      height: 10,
                      decoration:
                          BoxDecoration(color: color, shape: BoxShape.circle)),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(e.value.label,
                        style:
                            const TextStyle(color: AppColors.foreground, fontSize: 12),
                        overflow: TextOverflow.ellipsis),
                  ),
                  Text(e.value.value.toInt().toString(),
                      style: const TextStyle(
                          color: AppColors.mutedFg, fontSize: 12)),
                ]),
              );
            }).toList(),
          ),
        ),
      ],
    );
  }
}

class _RankingsTab extends ConsumerWidget {
  const _RankingsTab({required this.top, required this.onTopChanged});
  final int top;
  final ValueChanged<int> onTopChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(rankingsProvider(top));
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Row(
            children: [
              const Text('Top: ',
                  style: TextStyle(color: AppColors.mutedFg, fontSize: 14)),
              ...([10, 20, 50]).map((t) {
                final sel = t == top;
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: FilterChip(
                    label: Text('$t'),
                    selected: sel,
                    onSelected: (_) => onTopChanged(t),
                    selectedColor: AppColors.primary.withValues(alpha: 0.2),
                    labelStyle: TextStyle(
                        color: sel ? AppColors.primary : AppColors.mutedFg,
                        fontSize: 12),
                  ),
                );
              }),
            ],
          ),
        ),
        Expanded(
          child: async.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => AppEmptyState(
                message: 'Error al cargar rankings',
                description: e.toString()),
            data: (items) => items.isEmpty
                ? const AppEmptyState(message: 'Sin datos')
                : ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: items.length,
                    itemBuilder: (_, i) => _RankingRow(item: items[i]),
                  ),
          ),
        ),
      ],
    );
  }
}

class _RankingRow extends StatelessWidget {
  const _RankingRow({required this.item});
  final RankingItem item;

  @override
  Widget build(BuildContext context) {
    final isTop3 = item.posicion <= 3;
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 0),
      leading: Container(
        width: 32,
        height: 32,
        decoration: BoxDecoration(
          color: isTop3
              ? AppColors.primary.withValues(alpha: 0.15)
              : AppColors.secondary,
          borderRadius: BorderRadius.circular(6),
        ),
        alignment: Alignment.center,
        child: Text('${item.posicion}',
            style: TextStyle(
                color: isTop3 ? AppColors.primary : AppColors.mutedFg,
                fontWeight: FontWeight.bold,
                fontSize: 13)),
      ),
      title: Text(item.nombre, style: AppTextStyles.bodyMedium),
      trailing: Text('${item.valor}',
          style: AppTextStyles.titleMedium.copyWith(
              color: AppColors.primary, fontWeight: FontWeight.bold)),
    );
  }
}

class _MontanasTab extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(montanaRankingProvider);
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
          message: 'Error al cargar', description: e.toString()),
      data: (items) => items.isEmpty
          ? const AppEmptyState(message: 'Sin datos de montañas')
          : ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: items.length,
              itemBuilder: (_, i) {
                final item = items[i];
                return ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: AppColors.primary.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    alignment: Alignment.center,
                    child: Text('${i + 1}',
                        style: const TextStyle(
                            color: AppColors.primary,
                            fontWeight: FontWeight.bold,
                            fontSize: 13)),
                  ),
                  title: Text(item.nombre, style: AppTextStyles.bodyMedium),
                  trailing: Text('${item.totalAscensos} ascensos',
                      style: AppTextStyles.bodySmall
                          .copyWith(color: AppColors.mutedFg)),
                );
              },
            ),
    );
  }
}
