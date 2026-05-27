import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../estadisticas/domain/models/estadisticas_models.dart';
import '../../../estadisticas/presentation/providers/estadisticas_provider.dart';
import '../../domain/models/notificacion_models.dart';
import '../providers/notificaciones_provider.dart';

class NotificacionesScreen extends ConsumerWidget {
  const NotificacionesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final aprobaciones = ref.watch(aprobacionesPendientesProvider);
    final alertas = ref.watch(alertasSinJefeProvider);

    final authVal = ref.watch(authNotifierProvider).asData?.value;
    final role = authVal is AuthAuthenticated ? authVal.user.rol : null;
    final isPrivilegiado = role == UserRole.admin ||
        role == UserRole.secretaria ||
        role == UserRole.directivo;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Notificaciones'), centerTitle: false),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(aprobacionesPendientesProvider);
          ref.invalidate(alertasSinJefeProvider);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // ── Alertas: salidas sin jefe (solo privilegiados) ──────────────
            if (isPrivilegiado) ...[
              _SectionTitle(
                'Salidas sin Jefe de Salida',
                icon: Icons.warning_amber_rounded,
                iconColor: AppColors.destructive,
              ),
              alertas.when(
                loading: () => const Padding(
                  padding: EdgeInsets.all(24),
                  child: Center(child: CircularProgressIndicator()),
                ),
                error: (e, _) => AppEmptyState(
                    message: 'Error al cargar', error: e),
                data: (items) => items.isEmpty
                    ? const _EmptyHint(
                        icon: Icons.verified_outlined,
                        text: 'Todas las salidas tienen jefe asignado')
                    : Column(
                        children:
                            items.map((s) => _AlertaCard(alerta: s)).toList(),
                      ),
              ),
              const SizedBox(height: 24),
            ],

            // ── Aprobaciones de riesgo ────────────────────────────────────
            _SectionTitle(
              'Aprobaciones pendientes',
              icon: Icons.check_circle_outline,
              iconColor: const Color(0xFFD4A84B),
            ),
            aprobaciones.when(
              loading: () => const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => AppEmptyState(
                  message: 'Error al cargar', error: e),
              data: (items) => items.isEmpty
                  ? const _EmptyHint(
                      icon: Icons.fact_check_outlined,
                      text: 'Sin aprobaciones pendientes')
                  : Column(
                      children: items
                          .map((a) => _AprobacionCard(aprobacion: a))
                          .toList(),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Section header ────────────────────────────────────────────────────────────

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text, {required this.icon, required this.iconColor});
  final String text;
  final IconData icon;
  final Color iconColor;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(
          children: [
            Icon(icon, size: 18, color: iconColor),
            const SizedBox(width: 6),
            Text(text,
                style: AppTextStyles.titleMedium
                    .copyWith(fontWeight: FontWeight.w700)),
          ],
        ),
      );
}

class _EmptyHint extends StatelessWidget {
  const _EmptyHint({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Row(
          children: [
            Icon(icon, color: AppColors.mutedFg, size: 18),
            const SizedBox(width: 8),
            Text(text,
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
          ],
        ),
      );
}

// ─── Aprobación card ──────────────────────────────────────────────────────────

class _AprobacionCard extends ConsumerStatefulWidget {
  const _AprobacionCard({required this.aprobacion});
  final AprobacionPendiente aprobacion;

  @override
  ConsumerState<_AprobacionCard> createState() => _AprobacionCardState();
}

class _AprobacionCardState extends ConsumerState<_AprobacionCard> {
  bool _loading = false;

  @override
  Widget build(BuildContext context) {
    final a = widget.aprobacion;
    final df = DateFormat('dd/MM/yyyy');
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Nombre del socio — tappable para ver historial
          GestureDetector(
            onTap: () => _showHistorial(context, a.socioId, a.socioNombre),
            child: Text(
              a.socioNombre,
              style: AppTextStyles.bodyMedium.copyWith(
                fontWeight: FontWeight.w700,
                color: AppColors.primary,
                decoration: TextDecoration.underline,
                decorationColor: AppColors.primary,
              ),
            ),
          ),
          const SizedBox(height: 2),
          // Salida — link de navegación
          InkWell(
            onTap: () => context.push('/salidas/${a.salidaId}'),
            child: Text(
              a.salidaNombre +
                  (a.fechaSalida != null
                      ? ' · ${df.format(a.fechaSalida!)}'
                      : ''),
              style: AppTextStyles.bodySmall
                  .copyWith(color: AppColors.mutedFg),
            ),
          ),
          const SizedBox(height: 8),
          // Niveles
          Row(
            children: [
              Icon(Icons.signal_cellular_alt_outlined,
                  size: 14, color: AppColors.mutedFg),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  'Nivel socio: ${a.nivelSocio ?? '—'}  ·  '
                  'Mínimo: ${a.nivelMinimo ?? '—'}',
                  style: AppTextStyles.labelSmall
                      .copyWith(color: AppColors.mutedFg),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          // Badges de aprobación
          Wrap(spacing: 8, children: [
            _AprobBadge('Jefe de Montaña', a.aprobadoPorDirectivo),
            _AprobBadge('Jefe de Salida', a.aprobadoPorJefe),
          ]),
          const SizedBox(height: 10),
          // Botones de acción
          if (_loading)
            const Center(
                child: Padding(
              padding: EdgeInsets.all(4),
              child: SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2)),
            ))
          else
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.close, size: 16),
                    label: const Text('Negar'),
                    style: OutlinedButton.styleFrom(
                        foregroundColor: AppColors.destructive),
                    onPressed: () => _decidir(aprobar: false),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilledButton.icon(
                    icon: const Icon(Icons.check, size: 16),
                    label: const Text('Aprobar'),
                    onPressed: () => _decidir(aprobar: true),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }

  void _showHistorial(BuildContext context, String socioId, String nombre) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.card,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => DraggableScrollableSheet(
        initialChildSize: 0.65,
        maxChildSize: 0.95,
        minChildSize: 0.4,
        expand: false,
        builder: (_, scrollCtrl) => Consumer(
          builder: (context, ref, _) {
            final historialAsync = ref.watch(historialSocioProvider(socioId));
            return Column(
              children: [
                Container(
                  margin: const EdgeInsets.symmetric(vertical: 12),
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: AppColors.border,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
                  child: Row(
                    children: [
                      const Icon(Icons.terrain_outlined,
                          color: AppColors.primary, size: 20),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Historial de $nombre',
                          style: AppTextStyles.titleSmall
                              .copyWith(fontWeight: FontWeight.w600),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
                const Divider(color: AppColors.border, height: 1),
                Expanded(
                  child: historialAsync.when(
                    loading: () =>
                        const Center(child: CircularProgressIndicator()),
                    error: (e, _) => Center(
                      child: Text('Error al cargar historial',
                          style: AppTextStyles.bodySmall
                              .copyWith(color: AppColors.mutedFg)),
                    ),
                    data: (h) =>
                        _HistorialContent(historial: h, scrollCtrl: scrollCtrl),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Future<void> _decidir({required bool aprobar}) async {
    final motivo = await _pedirMotivo(aprobar: aprobar);
    if (motivo == null || motivo.trim().isEmpty) return;
    setState(() => _loading = true);
    try {
      await ref.read(notificacionesRepositoryProvider).decidirRiesgo(
            salidaId: widget.aprobacion.salidaId,
            participanteId: widget.aprobacion.participanteId,
            aprobar: aprobar,
            motivo: motivo.trim(),
          );
      if (!mounted) return;
      ref.invalidate(aprobacionesPendientesProvider);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text(aprobar
                ? 'Aprobación registrada'
                : 'Inscripción negada')),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error: $e')),
      );
    }
  }

  Future<String?> _pedirMotivo({required bool aprobar}) {
    final ctrl = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.sidebar,
        title: Text(aprobar ? 'Aprobar riesgo' : 'Negar riesgo'),
        content: TextField(
          controller: ctrl,
          maxLines: 3,
          maxLength: 500,
          decoration: const InputDecoration(
            labelText: 'Motivo (obligatorio)',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, ctrl.text),
            child: Text(aprobar ? 'Aprobar' : 'Negar'),
          ),
        ],
      ),
    );
  }
}

// ─── Historial bottom-sheet content ───────────────────────────────────────────

class _HistorialContent extends StatelessWidget {
  const _HistorialContent(
      {required this.historial, required this.scrollCtrl});
  final SocioHistorial historial;
  final ScrollController scrollCtrl;

  @override
  Widget build(BuildContext context) {
    return ListView(
      controller: scrollCtrl,
      padding: const EdgeInsets.all(16),
      children: [
        Row(
          children: [
            _StatChip('Total salidas',
                '${historial.totalParticipaciones}', AppColors.foreground),
            const SizedBox(width: 12),
            _StatChip('Cumbres logradas',
                '${historial.totalCumbresLogradas}', AppColors.salidaRealizada),
          ],
        ),
        const SizedBox(height: 16),
        if (historial.historial.isEmpty)
          Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(
                'Sin salidas registradas',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg),
              ),
            ),
          )
        else
          ...historial.historial.map((item) => _HistorialItem(item: item)),
      ],
    );
  }
}

class _StatChip extends StatelessWidget {
  const _StatChip(this.label, this.value, this.valueColor);
  final String label;
  final String value;
  final Color valueColor;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: AppTextStyles.labelSmall
                  .copyWith(color: AppColors.mutedFg)),
          Text(value,
              style: AppTextStyles.headlineMedium.copyWith(
                  fontWeight: FontWeight.bold, color: valueColor)),
        ],
      );
}

class _HistorialItem extends StatelessWidget {
  const _HistorialItem({required this.item});
  final SalidaHistorialItem item;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('d MMM yyyy', 'es');
    final cumbre = item.seRealizo;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            cumbre == true
                ? Icons.check_circle_outline
                : cumbre == false
                    ? Icons.cancel_outlined
                    : Icons.remove_circle_outline,
            size: 16,
            color: cumbre == true
                ? AppColors.salidaRealizada
                : AppColors.mutedFg,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.mountainNombre ?? item.salidaNombre,
                    style: AppTextStyles.bodySmall
                        .copyWith(fontWeight: FontWeight.w600)),
                if (item.rutaNombre != null)
                  Text(item.rutaNombre!,
                      style: AppTextStyles.labelSmall
                          .copyWith(color: AppColors.mutedFg)),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              if (item.mountainAltitud != null && item.mountainAltitud! > 0)
                Text('${item.mountainAltitud} m',
                    style: AppTextStyles.labelSmall
                        .copyWith(color: AppColors.mutedFg)),
              if (item.fecha != null)
                Text(df.format(item.fecha!),
                    style: AppTextStyles.labelSmall
                        .copyWith(color: AppColors.mutedFg)),
            ],
          ),
        ],
      ),
    );
  }
}

// ─── Approval badge ───────────────────────────────────────────────────────────

class _AprobBadge extends StatelessWidget {
  const _AprobBadge(this.label, this.done);
  final String label;
  final bool done;

  @override
  Widget build(BuildContext context) {
    final color = done ? AppColors.salidaRealizada : AppColors.mutedFg;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(done ? Icons.check : Icons.hourglass_empty,
              size: 11, color: color),
          const SizedBox(width: 4),
          Text(label,
              style: TextStyle(
                  color: color,
                  fontSize: 10,
                  fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

// ─── Alerta card ──────────────────────────────────────────────────────────────

class _AlertaCard extends StatelessWidget {
  const _AlertaCard({required this.alerta});
  final AlertaSinJefe alerta;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd/MM/yyyy');
    return AppCard(
      onTap: () => context.push('/salidas/${alerta.salidaId}'),
      child: Row(
        children: [
          const Icon(Icons.report_gmailerrorred_outlined,
              color: AppColors.destructive, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(alerta.salidaNombre,
                    style: AppTextStyles.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
                if (alerta.jefeAbandonoNombre != null)
                  Text(
                    '${alerta.jefeAbandonoNombre} se retiró como Jefe de Salida.',
                    style: AppTextStyles.labelSmall
                        .copyWith(color: AppColors.mutedFg),
                  ),
              ],
            ),
          ),
          if (alerta.fechaSalida != null)
            Text(df.format(alerta.fechaSalida!),
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
        ],
      ),
    );
  }
}
