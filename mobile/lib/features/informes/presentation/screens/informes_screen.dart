import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../domain/models/informe_model.dart';
import '../providers/informes_provider.dart';

class InformesScreen extends ConsumerStatefulWidget {
  const InformesScreen({super.key});

  @override
  ConsumerState<InformesScreen> createState() => _InformesScreenState();
}

class _InformesScreenState extends ConsumerState<InformesScreen>
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
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Informes'),
        centerTitle: false,
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Pendientes'),
            Tab(text: 'Todos'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _PendientesTab(),
          AppPagedList<InformeResumen>(
            loader: (page) =>
                ref.read(informesRepositoryProvider).getInformes(page: page),
            emptyMessage: 'Sin informes',
            itemBuilder: (_, item, _) => _InformeItem(item: item),
          ),
        ],
      ),
    );
  }
}

class _PendientesTab extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(pendientesJefeProvider);
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => AppEmptyState(
          message: 'Error al cargar informes', error: e),
      data: (items) => items.isEmpty
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.check_circle_outline,
                      color: AppColors.salidaRealizada, size: 48),
                  const SizedBox(height: 12),
                  Text('Sin informes pendientes',
                      style: TextStyle(color: AppColors.mutedFg)),
                ],
              ),
            )
          : RefreshIndicator(
              onRefresh: () async =>
                  ref.invalidate(pendientesJefeProvider),
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: items.length,
                itemBuilder: (_, i) => _InformeItem(item: items[i]),
              ),
            ),
    );
  }
}

class _InformeItem extends StatelessWidget {
  const _InformeItem({required this.item});
  final InformeResumen item;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd/MM/yyyy', 'es');
    final estado = item.estado ?? 'PENDIENTE';
    final color = switch (estado.toUpperCase()) {
      'VALIDADO' => AppColors.salidaRealizada,
      'COMPLETADO' => AppColors.salidaEnCurso,
      _ => AppColors.salidaPlanificada,
    };

    return AppCard(
      onTap: () => context.push('/informes/${item.salidaId}'),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.salidaNombre,
                    style: AppTextStyles.bodyLarge
                        .copyWith(fontWeight: FontWeight.w600)),
                if (item.fechaSalida != null) ...[
                  const SizedBox(height: 4),
                  Text(df.format(item.fechaSalida!),
                      style: AppTextStyles.bodySmall
                          .copyWith(color: AppColors.mutedFg)),
                ],
                if (item.esJefe) ...[
                  const SizedBox(height: 4),
                  const Text('Eres jefe de salida',
                      style: TextStyle(
                          color: AppColors.primary,
                          fontSize: 12,
                          fontWeight: FontWeight.w500)),
                ],
              ],
            ),
          ),
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: color.withValues(alpha: 0.3)),
            ),
            child: Text(estado,
                style: TextStyle(
                    color: color,
                    fontSize: 11,
                    fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}
