import 'package:flutter/material.dart';
import '../theme/app_colors.dart';
import '../theme/app_theme.dart';

enum SalidaStatus { planificada, enCurso, realizada, cancelada }

extension SalidaStatusX on SalidaStatus {
  String get label => switch (this) {
    SalidaStatus.planificada => 'PLANIFICADA',
    SalidaStatus.enCurso     => 'EN CURSO',
    SalidaStatus.realizada   => 'REALIZADA',
    SalidaStatus.cancelada   => 'CANCELADA',
  };
  Color get color => switch (this) {
    SalidaStatus.planificada => AppColors.salidaPlanificada,
    SalidaStatus.enCurso     => AppColors.salidaEnCurso,
    SalidaStatus.realizada   => AppColors.salidaRealizada,
    SalidaStatus.cancelada   => AppColors.salidaCancelada,
  };

  static SalidaStatus fromString(String value) =>
      SalidaStatus.values.firstWhere(
        (s) => s.name.toUpperCase() ==
            value.replaceAll(' ', '').replaceAll('_', '').toUpperCase(),
        orElse: () => SalidaStatus.planificada,
      );
}

class AppStatusBadge extends StatelessWidget {
  const AppStatusBadge({required this.status, super.key});
  final SalidaStatus status;

  @override
  Widget build(BuildContext context) {
    final color = status.color;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(AppTheme.radiusSm),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        status.label,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.3,
        ),
      ),
    );
  }
}
