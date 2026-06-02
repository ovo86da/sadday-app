import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';
import '../../features/dashboard/domain/models/dashboard_models.dart';

class JefeSalidaBanner extends StatelessWidget {
  const JefeSalidaBanner({super.key, required this.data});
  final JefeAlertasData data;

  @override
  Widget build(BuildContext context) {
    final pendientes = data.aprobacionesPendientes.length;
    final sinJefe = data.salidasSinJefe.length;
    final df = DateFormat('dd/MM/yyyy');

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.salidaPlanificada.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
            color: AppColors.salidaPlanificada.withValues(alpha: 0.4)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.workspace_premium_outlined,
                  color: AppColors.salidaPlanificada, size: 20),
              const SizedBox(width: 8),
              Text('Jefe de Salida',
                  style: AppTextStyles.titleMedium
                      .copyWith(fontWeight: FontWeight.w700)),
            ],
          ),
          if (pendientes > 0) ...[
            const SizedBox(height: 10),
            _JefeAlert(
              icon: Icons.fact_check_outlined,
              color: AppColors.salidaPlanificada,
              text: 'Tienes $pendientes aprobación'
                  '${pendientes != 1 ? 'es' : ''} de riesgo pendiente'
                  '${pendientes != 1 ? 's' : ''} de revisar',
            ),
          ],
          if (sinJefe > 0) ...[
            const SizedBox(height: 8),
            _JefeAlert(
              icon: Icons.report_gmailerrorred_outlined,
              color: AppColors.destructive,
              text: '$sinJefe salida${sinJefe != 1 ? 's' : ''} '
                  'sin Jefe de Salida asignado',
            ),
          ],
          if (data.proximasComoJefe.isNotEmpty) ...[
            const SizedBox(height: 10),
            Text('Tus próximas salidas como jefe',
                style:
                    AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
            const SizedBox(height: 4),
            ...data.proximasComoJefe.map((s) => InkWell(
                  onTap: () => context.push('/salidas/${s.salidaId}'),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(s.salidaNombre,
                                  style: AppTextStyles.bodyMedium.copyWith(
                                      fontWeight: FontWeight.w600)),
                              if (s.montanaNombre != null)
                                Text(s.montanaNombre!,
                                    style: AppTextStyles.labelSmall.copyWith(
                                        color: AppColors.mutedFg)),
                            ],
                          ),
                        ),
                        if (s.fecha != null)
                          Text(df.format(s.fecha!),
                              style: AppTextStyles.bodySmall
                                  .copyWith(fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ),
                )),
          ],
        ],
      ),
    );
  }
}

class _JefeAlert extends StatelessWidget {
  const _JefeAlert(
      {required this.icon, required this.color, required this.text});
  final IconData icon;
  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(text,
                style: TextStyle(
                    color: color,
                    fontSize: 13,
                    fontWeight: FontWeight.w500)),
          ),
        ],
      ),
    );
  }
}
