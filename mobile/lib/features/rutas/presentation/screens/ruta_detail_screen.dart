import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/app_exception.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../../core/widgets/app_input.dart';
import '../providers/rutas_provider.dart';

class RutaDetailScreen extends ConsumerStatefulWidget {
  const RutaDetailScreen({required this.id, super.key});
  final int id;

  @override
  ConsumerState<RutaDetailScreen> createState() => _RutaDetailScreenState();
}

class _RutaDetailScreenState extends ConsumerState<RutaDetailScreen> {
  bool _saving = false;

  bool get _canReview {
    final auth = ref.read(authNotifierProvider).asData?.value;
    if (auth is! AuthAuthenticated) return false;
    return auth.user.rol == UserRole.admin || auth.user.rol == UserRole.directivo;
  }

  Future<void> _aprobar() async {
    setState(() => _saving = true);
    try {
      await ref.read(rutasRepositoryProvider).aprobarRuta(widget.id);
      ref.invalidate(rutaDetailProvider(widget.id));
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Ruta aprobada correctamente')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(unwrapDio(e).toString()), backgroundColor: AppColors.destructive),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _showRechazarDialog() async {
    final controller = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.background,
        title: const Text('Rechazar ruta'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Indica el motivo del rechazo. El proponente podrá verlo al consultar la ruta.',
              style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
            ),
            const SizedBox(height: 12),
            AppInput(
              controller: controller,
              label: 'Motivo *',
              hint: 'Ej: Información técnica incompleta...',
              maxLines: 3,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancelar'),
          ),
          StatefulBuilder(
            builder: (ctx2, setSt) => TextButton(
              onPressed: () {
                if (controller.text.trim().isNotEmpty) Navigator.pop(ctx, true);
              },
              child: const Text('Rechazar', style: TextStyle(color: AppColors.destructive)),
            ),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      final motivo = controller.text.trim();
      if (motivo.isEmpty) return;
      setState(() => _saving = true);
      try {
        await ref.read(rutasRepositoryProvider).rechazarRuta(widget.id, motivo);
        ref.invalidate(rutaDetailProvider(widget.id));
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Ruta rechazada')),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(unwrapDio(e).toString()), backgroundColor: AppColors.destructive),
          );
        }
      } finally {
        if (mounted) setState(() => _saving = false);
      }
    }
    controller.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(rutaDetailProvider(widget.id));

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Ruta')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => AppEmptyState(message: 'Error al cargar', error: e),
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
                  border: Border.all(color: AppColors.salidaPlanificada.withValues(alpha: 0.4)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.warning_amber_outlined, color: AppColors.salidaPlanificada, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Esta ruta requiere documentación de acceso',
                        style: TextStyle(color: AppColors.salidaPlanificada, fontSize: 13),
                      ),
                    ),
                  ],
                ),
              ),

            // Motivo de rechazo
            if (r.isRechazada && r.motivoRechazo != null)
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.destructive.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.destructive.withValues(alpha: 0.3)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Row(
                      children: [
                        Icon(Icons.cancel_outlined, color: AppColors.destructive, size: 16),
                        SizedBox(width: 6),
                        Text('Motivo del rechazo',
                            style: TextStyle(color: AppColors.destructive, fontSize: 12, fontWeight: FontWeight.w600)),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(r.motivoRechazo!, style: AppTextStyles.bodySmall.copyWith(color: AppColors.foreground)),
                  ],
                ),
              ),

            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(r.nombre,
                      style: AppTextStyles.headlineMedium.copyWith(fontWeight: FontWeight.bold)),
                  if (r.lugarDisplay.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(r.lugarDisplay, style: AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg)),
                  ],
                  const SizedBox(height: 16),
                  const Divider(color: AppColors.border),
                  const SizedBox(height: 12),
                  if (r.tipoActividad != null) _Row('Tipo', r.tipoActividad!),
                  if (r.nivelMinimo != null) _Row('Nivel mínimo', r.nivelMinimo!),
                  if (r.longitud != null) _Row('Longitud', '${r.longitud!.toStringAsFixed(1)} km'),
                  if (r.desnivel != null) _Row('Desnivel', '+${r.desnivel!.round()} m'),
                  if (r.duracion != null) _Row('Duración', r.duracion!),
                  if (r.estado != null)
                    _Row('Estado', r.estado!, valueColor: _estadoColor(r.estado!)),
                  if (r.descripcion != null) ...[
                    const SizedBox(height: 12),
                    Text('Descripción',
                        style: AppTextStyles.titleSmall.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 6),
                    Text(r.descripcion!, style: AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg)),
                  ],
                ],
              ),
            ),

            // ── Cumbres (solo INTEGRAL) ───────────────────────────
            if (r.isIntegral && r.integral != null && r.integral!.cumbres.isNotEmpty) ...[
              const SizedBox(height: 12),
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Cumbres (${r.integral!.cumbres.length})',
                        style: AppTextStyles.titleSmall.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 10),
                    ...r.integral!.cumbres.map((c) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        children: [
                          Container(
                            width: 22, height: 22,
                            alignment: Alignment.center,
                            decoration: BoxDecoration(
                              color: AppColors.primary.withValues(alpha: 0.15),
                              shape: BoxShape.circle,
                            ),
                            child: Text('${c.secuencia}',
                                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: AppColors.primary)),
                          ),
                          const SizedBox(width: 10),
                          const Icon(Icons.landscape_outlined, size: 14, color: AppColors.mutedFg),
                          const SizedBox(width: 6),
                          Expanded(child: Text(c.mountainNombre, style: AppTextStyles.bodyMedium)),
                          if (c.altitud != null)
                            Text('${c.altitud} m',
                                style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
                        ],
                      ),
                    )),
                    if (r.integral!.descripcionItinerario != null) ...[
                      const Divider(color: AppColors.border),
                      const SizedBox(height: 8),
                      Text('Itinerario',
                          style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 4),
                      Text(r.integral!.descripcionItinerario!,
                          style: AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg)),
                    ],
                  ],
                ),
              ),
            ],

            // ── Dificultad técnica (todas las rutas) ──────────────
            if (r.tipoActividad == 'ALPINISMO' && r.alpinismo != null ||
                r.isIntegral && r.integral?.dificultadMaxTipo == 'ALPINISMO' && r.alpinismo != null) ...[
              const SizedBox(height: 12),
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(r.isIntegral ? 'Dificultad del tramo más difícil — Alpinismo' : 'Dificultad técnica',
                        style: AppTextStyles.titleSmall.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 10),
                    _Row('IFAS', r.alpinismo!.escalaAlpinaIfasGrado),
                    _Row('Roca UIAA', r.alpinismo!.dificultadRocaUiaa),
                    _Row('Hielo WI', r.alpinismo!.dificultadHieloGrado),
                    _Row('Compromiso', r.alpinismo!.compromisoTipo),
                    _Row('Yosemite', r.alpinismo!.yosemiteTipo),
                    _Row('Nivel técnico', r.alpinismo!.saddayNivelTecnicoEscala),
                    _Row('Nivel físico', r.alpinismo!.saddayNivelFisicoEscala),
                    if (r.alpinismo!.equipoMontanaNombre != null)
                      _Row('Equipo', r.alpinismo!.equipoMontanaNombre!),
                  ],
                ),
              ),
            ],
            if (r.tipoActividad == 'ESCALADA' && r.escalada != null ||
                r.isIntegral && r.integral?.dificultadMaxTipo == 'ESCALADA' && r.escalada != null) ...[
              const SizedBox(height: 12),
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(r.isIntegral ? 'Dificultad del tramo más difícil — Escalada' : 'Dificultad técnica',
                        style: AppTextStyles.titleSmall.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 10),
                    _Row('Grado roca', r.escalada!.dificultadRocaUiaa),
                    _Row('Tipo', r.escalada!.tipoEscalada),
                    if (r.escalada!.numCintas != null) _Row('N° cintas', '${r.escalada!.numCintas}'),
                    if (r.escalada!.alturaViaM != null) _Row('Altura vía', '${r.escalada!.alturaViaM} m'),
                    if (r.escalada!.tipoRoca != null) _Row('Tipo roca', r.escalada!.tipoRoca!),
                  ],
                ),
              ),
            ],
            if (r.tipoActividad == 'TREKKING' && r.trekking != null ||
                r.isIntegral && r.integral?.dificultadMaxTipo == 'TREKKING' && r.trekking != null) ...[
              const SizedBox(height: 12),
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(r.isIntegral ? 'Dificultad del tramo más difícil — Trekking' : 'Características',
                        style: AppTextStyles.titleSmall.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 10),
                    _Row('Dificultad', r.trekking!.dificultadNombre),
                    _Row('Tipo ruta', r.trekking!.esCircular ? 'Circular' : 'Ida y vuelta'),
                    _Row('Fuentes agua', r.trekking!.fuentesAgua ? 'Sí' : 'No'),
                    if (r.trekking!.tipoTerreno != null) _Row('Terreno', r.trekking!.tipoTerreno!),
                  ],
                ),
              ),
            ],
            if (r.tipoActividad == 'CICLISMO' && r.ciclismo != null ||
                r.isIntegral && r.integral?.dificultadMaxTipo == 'CICLISMO' && r.ciclismo != null) ...[
              const SizedBox(height: 12),
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(r.isIntegral ? 'Dificultad del tramo más difícil — Ciclismo' : 'Características',
                        style: AppTextStyles.titleSmall.copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 10),
                    _Row('Bicicleta', r.ciclismo!.tipoBicicleta),
                    if (r.ciclismo!.dificultadTecnica != null) _Row('Dificultad tec.', r.ciclismo!.dificultadTecnica!),
                    if (r.ciclismo!.superficiePredominante != null) _Row('Superficie', r.ciclismo!.superficiePredominante!),
                    if (r.ciclismo!.ciclabilidadPct != null) _Row('Ciclabilidad', '${r.ciclismo!.ciclabilidadPct}%'),
                  ],
                ),
              ),
            ],

            // Acciones de revisión (Admin/Directivo)
            if (_canReview) ...[
              const SizedBox(height: 16),
              Row(
                children: [
                  if (!r.isAprobada)
                    Expanded(
                      child: AppButton(
                        label: 'Aprobar',
                        loading: _saving,
                        onPressed: _saving ? null : _aprobar,
                      ),
                    ),
                  if (r.isPendiente) const SizedBox(width: 12),
                  if (r.isPendiente)
                    Expanded(
                      child: AppButton(
                        label: 'Rechazar',
                        variant: AppButtonVariant.destructive,
                        loading: _saving,
                        onPressed: _saving ? null : _showRechazarDialog,
                      ),
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Color _estadoColor(String estado) => switch (estado) {
        'APROBADA' => const Color(0xFF48BB78),
        'RECHAZADA' => AppColors.destructive,
        _ => AppColors.mutedFg,
      };
}

class _Row extends StatelessWidget {
  const _Row(this.label, this.value, {this.valueColor});
  final String label;
  final String value;
  final Color? valueColor;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(
          children: [
            SizedBox(
              width: 100,
              child: Text(label, style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
            ),
            Expanded(
              child: Text(value,
                  style: AppTextStyles.bodyMedium.copyWith(
                    color: valueColor,
                    fontWeight: valueColor != null ? FontWeight.w600 : null,
                  )),
            ),
          ],
        ),
      );
}
