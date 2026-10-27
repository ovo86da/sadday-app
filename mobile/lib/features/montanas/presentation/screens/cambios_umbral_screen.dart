import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/api/app_exception.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../domain/models/cambio_umbral_model.dart';
import '../providers/montanas_provider.dart';

/// Cambios de umbral de acceso esperando una segunda aprobación (FR-026).
///
/// Ocupa su propia pantalla y no un botón en una notificación, por decisión
/// expresa: lo que manda es que se vea con claridad **qué cambia y qué se está
/// aprobando**, no que quepa en poco sitio. Estos umbrales deciden a qué
/// dificultad puede inscribirse cada socio, y aprobar uno sin ver qué dice es
/// el descuido que este flujo existe para evitar.
class CambiosUmbralScreen extends ConsumerStatefulWidget {
  const CambiosUmbralScreen({super.key});

  @override
  ConsumerState<CambiosUmbralScreen> createState() => _CambiosUmbralScreenState();
}

class _CambiosUmbralScreenState extends ConsumerState<CambiosUmbralScreen> {
  String? _resolviendo;

  Future<void> _aprobar(CambioUmbral c) async {
    // Se confirma aunque la pantalla ya enseñe el detalle: aplicar un umbral
    // relajado es irreversible en la práctica, porque desde ese momento hay
    // gente inscribiéndose con él.
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Aprobar el cambio de «${c.nivelSocioNombre}»'),
        content: Text(
          c.algunaRelaja
              ? 'Este cambio PERMITE MÁS de lo que permitía antes. Desde que lo '
                'apruebes, socios de ese nivel podrán inscribirse a rutas que '
                'hasta ahora no podían.'
              : 'Este cambio exige más que antes. Se aplicará en cuanto lo apruebes.',
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancelar')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Aprobar')),
        ],
      ),
    );
    if (ok != true) return;
    await _ejecutar(c.id, () => ref
        .read(montanasRepositoryProvider)
        .aprobarCambioUmbral(c.id), 'Cambio aprobado y aplicado');
  }

  Future<void> _rechazar(CambioUmbral c) async {
    final controlador = TextEditingController();
    final motivo = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Rechazar el cambio'),
        content: TextField(
          controller: controlador,
          autofocus: true,
          maxLines: 3,
          maxLength: 1000,
          decoration: const InputDecoration(
            // Sin motivo, quien lo pidió no sabe si corregir o insistir.
            hintText: 'Por qué se rechaza. Quien lo pidió va a leerlo.',
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, controlador.text.trim()),
              child: const Text('Rechazar')),
        ],
      ),
    );
    if (motivo == null || motivo.isEmpty) return;
    await _ejecutar(c.id, () => ref
        .read(montanasRepositoryProvider)
        .rechazarCambioUmbral(c.id, motivo), 'Cambio rechazado');
  }

  Future<void> _ejecutar(String id, Future<void> Function() accion, String ok) async {
    setState(() => _resolviendo = id);
    try {
      await accion();
      ref.invalidate(cambiosUmbralProvider);
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(ok)));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('No se pudo: ${unwrapDio(e)}')));
      }
    } finally {
      if (mounted) setState(() => _resolviendo = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(cambiosUmbralProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Cambios de umbral')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => AppEmptyState(message: 'Error al cargar', error: e),
        data: (cambios) => cambios.isEmpty
            ? const AppEmptyState(message: 'No hay cambios esperando aprobación')
            : RefreshIndicator(
                onRefresh: () async => ref.invalidate(cambiosUmbralProvider),
                child: ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: cambios.length,
                  itemBuilder: (_, i) => _Tarjeta(
                    cambio: cambios[i],
                    ocupado: _resolviendo == cambios[i].id,
                    onAprobar: () => _aprobar(cambios[i]),
                    onRechazar: () => _rechazar(cambios[i]),
                  ),
                ),
              ),
      ),
    );
  }
}

class _Tarjeta extends StatelessWidget {
  const _Tarjeta({
    required this.cambio,
    required this.ocupado,
    required this.onAprobar,
    required this.onRechazar,
  });

  final CambioUmbral cambio;
  final bool ocupado;
  final VoidCallback onAprobar;
  final VoidCallback onRechazar;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: AppCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Nivel «${cambio.nivelSocioNombre}»',
                style: AppTextStyles.titleSmall
                    .copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            Text('Lo pidió ${cambio.solicitadoPorNombre} · ${cambio.antiguedad}',
                style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
            const SizedBox(height: 2),
            Text('El umbral vigente sigue siendo el anterior.',
                style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),

            const SizedBox(height: 10),
            // Si de siete escalas solo se toca una, decirlo evita que haya que
            // recorrer la tabla buscándola.
            Text(
              cambio.cuantasCambian == 1
                  // El total sale de la lista y no de un 7 fijo: con FR-028
                  // pasaron a ser nueve, y un numero escrito a mano habria
                  // seguido diciendo siete sin que nada fallara.
                  ? 'Cambia 1 de las ${cambio.escalas.length} escalas:'
                  : 'Cambian ${cambio.cuantasCambian} de las ${cambio.escalas.length} escalas:',
              style: AppTextStyles.bodySmall,
            ),
            const SizedBox(height: 6),

            for (final e in cambio.escalas) _FilaEscala(escala: e),

            const SizedBox(height: 12),
            if (cambio.puedoAprobar)
              Row(children: [
                Expanded(
                  child: AppButton(
                    label: 'Aprobar y aplicar',
                    loading: ocupado,
                    onPressed: onAprobar,
                  ),
                ),
                const SizedBox(width: 8),
                AppButton(
                  label: 'Rechazar',
                  variant: AppButtonVariant.secondary,
                  onPressed: onRechazar,
                ),
              ])
            else
              // Decir por qué, en vez de limitarse a no dibujar el botón.
              Text(
                cambio.puedoCancelar
                    ? 'Lo pediste tú, así que tiene que aprobarlo otra persona '
                      'de la directiva.'
                    : 'No puedes resolver este cambio.',
                style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
              ),
          ],
        ),
      ),
    );
  }
}

class _FilaEscala extends StatelessWidget {
  const _FilaEscala({required this.escala});
  final CambioEscala escala;

  @override
  Widget build(BuildContext context) {
    final relaja = escala.sentido == 'RELAJA';
    final color = !escala.cambia
        ? AppColors.mutedFg
        : relaja
            ? Colors.amber
            : Colors.lightBlueAccent;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(children: [
        SizedBox(
          width: 108,
          child: Text(escala.escala,
              style: AppTextStyles.bodySmall.copyWith(
                  color: escala.cambia ? null : AppColors.mutedFg,
                  fontWeight: escala.cambia ? FontWeight.w600 : null)),
        ),
        Expanded(
          child: Text(
            escala.cambia
                ? '${escala.valorActual ?? "—"}  →  ${escala.valorPropuesto}'
                : escala.valorPropuesto,
            style: AppTextStyles.bodySmall.copyWith(
                color: escala.cambia ? null : AppColors.mutedFg),
          ),
        ),
        // Relajar es lo que de verdad pone a alguien en terreno nuevo, así que
        // se marca distinto de endurecer aunque el flujo sea el mismo.
        Text(
          !escala.cambia ? '=' : (relaja ? 'permite más' : 'exige más'),
          style: AppTextStyles.bodySmall.copyWith(color: color),
        ),
      ]),
    );
  }
}
