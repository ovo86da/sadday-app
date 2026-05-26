import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../domain/models/montana_model.dart';
import '../providers/montanas_provider.dart';

class MontanasScreen extends ConsumerStatefulWidget {
  const MontanasScreen({super.key});

  @override
  ConsumerState<MontanasScreen> createState() => _MontanasScreenState();
}

class _MontanasScreenState extends ConsumerState<MontanasScreen> {
  final _searchController = TextEditingController();
  String _q = '';
  int _listKey = 0;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _search(String q) => setState(() {
        _q = q;
        _listKey++;
      });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Montañas'),
        centerTitle: false,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Buscar montaña...',
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
      body: AppPagedList<Montana>(
        key: ValueKey(_listKey),
        loader: (page) => ref
            .read(montanasRepositoryProvider)
            .getMontanas(page: page, q: _q.isEmpty ? null : _q),
        emptyMessage: 'Sin montañas',
        itemBuilder: (_, m, _) => _MontanaItem(montana: m),
      ),
    );
  }
}

class _MontanaItem extends StatelessWidget {
  const _MontanaItem({required this.montana});
  final Montana montana;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      onTap: () => context.push('/montanas/${montana.id}'),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.landscape, color: AppColors.primary),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(montana.nombre,
                    style: AppTextStyles.bodyLarge
                        .copyWith(fontWeight: FontWeight.w600)),
                Row(children: [
                  if (montana.pais != null) ...[
                    const Icon(Icons.flag_outlined,
                        size: 13, color: AppColors.mutedFg),
                    const SizedBox(width: 4),
                    Text(montana.pais!,
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg)),
                    const SizedBox(width: 12),
                  ],
                  if (montana.altitud != null) ...[
                    const Icon(Icons.height, size: 13, color: AppColors.mutedFg),
                    const SizedBox(width: 4),
                    Text('${montana.altitud!.round()} m',
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg)),
                  ],
                ]),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text('${montana.numRutas}',
                  style: AppTextStyles.titleLarge
                      .copyWith(color: AppColors.primary, fontWeight: FontWeight.bold)),
              Text('rutas',
                  style: AppTextStyles.labelSmall.copyWith(color: AppColors.mutedFg)),
            ],
          ),
        ],
      ),
    );
  }
}
