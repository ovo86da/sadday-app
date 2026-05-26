import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../providers/rutas_provider.dart';

class RutaDetailScreen extends ConsumerWidget {
  const RutaDetailScreen({required this.id, super.key});
  final int id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(rutaDetailProvider(id));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Ruta')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => AppEmptyState(
            message: 'Error al cargar', error: e),
        data: (r) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (r.requierePermisos)
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.salidaPlanificada.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                      color:
                          AppColors.salidaPlanificada.withValues(alpha: 0.4)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.warning_amber_outlined,
                        color: AppColors.salidaPlanificada, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Esta ruta requiere documentación de acceso',
                        style: TextStyle(
                            color: AppColors.salidaPlanificada, fontSize: 13),
                      ),
                    ),
                  ],
                ),
              ),
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(r.nombre,
                      style: AppTextStyles.headlineMedium
                          .copyWith(fontWeight: FontWeight.bold)),
                  if (r.montanaNombre != null) ...[
                    const SizedBox(height: 4),
                    Text(r.montanaNombre!,
                        style: AppTextStyles.bodyMedium
                            .copyWith(color: AppColors.mutedFg)),
                  ],
                  const SizedBox(height: 16),
                  const Divider(color: AppColors.border),
                  const SizedBox(height: 12),
                  if (r.tipoActividad != null)
                    _Row('Tipo', r.tipoActividad!),
                  if (r.nivelMinimo != null)
                    _Row('Nivel mínimo', r.nivelMinimo!),
                  if (r.longitud != null)
                    _Row('Longitud', '${r.longitud!.toStringAsFixed(1)} km'),
                  if (r.desnivel != null)
                    _Row('Desnivel', '+${r.desnivel!.round()} m'),
                  if (r.duracion != null) _Row('Duración', r.duracion!),
                  if (r.estado != null) _Row('Estado', r.estado!),
                  if (r.descripcion != null) ...[
                    const SizedBox(height: 12),
                    Text('Descripción',
                        style: AppTextStyles.titleSmall
                            .copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    Text(r.descripcion!,
                        style: AppTextStyles.bodyMedium
                            .copyWith(color: AppColors.mutedFg)),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(
          children: [
            SizedBox(
              width: 100,
              child: Text(label,
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.mutedFg)),
            ),
            Expanded(child: Text(value, style: AppTextStyles.bodyMedium)),
          ],
        ),
      );
}
