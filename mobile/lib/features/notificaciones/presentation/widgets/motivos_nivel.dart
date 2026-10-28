import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../domain/models/notificacion_models.dart';

/// Por qué una inscripción está retenida, escala por escala (FR-028).
///
/// Existe porque «nivel insuficiente» no sirve para decidir: quien aprueba
/// necesita saber qué escala no da y por cuánto —«la ruta pide WI3 y su nivel
/// llega a WI2»—.
///
/// Los dos tipos de motivo se pintan distinto a propósito. Uno es un juicio
/// («no alcanza») y el otro es la ausencia de un dato («no lo sé»). Mezclarlos
/// en un mismo aviso llevaría a firmar un hueco de datos creyendo que se está
/// aceptando un riesgo medido, que es justo el fallo que FR-028 evita.
class MotivosNivel extends StatelessWidget {
  const MotivosNivel({super.key, required this.evaluacion});

  final EvaluacionNivel? evaluacion;

  @override
  Widget build(BuildContext context) {
    final e = evaluacion;
    if (e == null || e.hallazgos.isEmpty) return const SizedBox.shrink();

    final excede = e.queExceden;
    final huecos = e.sinComprobar;

    return Container(
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.sidebar,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (excede.isNotEmpty) ...[
            const _Encabezado(
              icono: Icons.warning_amber_rounded,
              color: AppColors.salidaPlanificada,
              texto: 'La ruta excede el nivel del socio',
            ),
            const SizedBox(height: 4),
            ...excede.map(
              (h) => _Linea(
                texto: h.escala != null
                    ? '${h.escala}: pide ${h.exigeRuta} · su nivel llega a '
                        '${h.permiteNivel}'
                    : h.detalle,
              ),
            ),
          ],
          if (excede.isNotEmpty && huecos.isNotEmpty) const SizedBox(height: 8),
          if (huecos.isNotEmpty) ...[
            const _Encabezado(
              icono: Icons.help_outline,
              color: AppColors.mutedFg,
              texto: 'Esto no se pudo comprobar',
            ),
            const SizedBox(height: 4),
            ...huecos.map((h) => _Linea(texto: h.detalle)),
            const SizedBox(height: 4),
            Text(
              'Falta un dato, no es un visto bueno: la inscripción se detiene '
              'igual.',
              style: AppTextStyles.labelSmall.copyWith(
                color: AppColors.mutedFg,
                fontStyle: FontStyle.italic,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _Encabezado extends StatelessWidget {
  const _Encabezado({
    required this.icono,
    required this.color,
    required this.texto,
  });

  final IconData icono;
  final Color color;
  final String texto;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Icon(icono, size: 14, color: color),
          const SizedBox(width: 4),
          Expanded(
            child: Text(
              texto,
              style: AppTextStyles.labelSmall
                  .copyWith(color: color, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      );
}

class _Linea extends StatelessWidget {
  const _Linea({required this.texto});

  final String texto;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 2),
        child: Text(
          texto,
          style: AppTextStyles.labelSmall.copyWith(color: AppColors.foreground),
        ),
      );
}
