import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../domain/models/ruta_model.dart';
import '../providers/rutas_provider.dart';

class RutasScreen extends ConsumerStatefulWidget {
  const RutasScreen({super.key});

  @override
  ConsumerState<RutasScreen> createState() => _RutasScreenState();
}

class _RutasScreenState extends ConsumerState<RutasScreen> {
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
        title: const Text('Rutas'),
        centerTitle: false,
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
      body: AppPagedList<Ruta>(
        key: ValueKey(_listKey),
        loader: (page) => ref
            .read(rutasRepositoryProvider)
            .getRutas(page: page, q: _q.isEmpty ? null : _q),
        emptyMessage: 'Sin rutas',
        itemBuilder: (_, r, _) => _RutaItem(ruta: r),
      ),
    );
  }
}

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
          const SizedBox(height: 4),
          if (ruta.montanaNombre != null)
            Text(ruta.montanaNombre!,
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            children: [
              if (ruta.tipoActividad != null) _Chip(ruta.tipoActividad!),
              if (ruta.nivelMinimo != null) _Chip('Nivel: ${ruta.nivelMinimo}'),
              if (ruta.longitud != null)
                _Chip('${ruta.longitud!.toStringAsFixed(1)} km'),
              if (ruta.desnivel != null)
                _Chip('+${ruta.desnivel!.round()} m'),
            ],
          ),
        ],
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip(this.label);
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
