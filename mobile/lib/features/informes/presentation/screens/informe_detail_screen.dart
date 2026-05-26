import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../domain/models/informe_model.dart';
import '../providers/informes_provider.dart';
import 'informe_crear_screen.dart';

class InformeDetailScreen extends ConsumerWidget {
  const InformeDetailScreen({required this.salidaId, super.key});
  final String salidaId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(informeDetailProvider(salidaId));
    final authVal = ref.watch(authNotifierProvider).asData?.value;
    final role = authVal is AuthAuthenticated ? authVal.user.rol : null;
    final canValidar = role == UserRole.admin || role == UserRole.directivo;
    final canEdit = role == UserRole.admin ||
        role == UserRole.secretaria ||
        role == UserRole.directivo;
    final canGeneratePdf = role == UserRole.admin ||
        role == UserRole.secretaria ||
        role == UserRole.directivo;

    void openForm({Informe? existing}) {
      Navigator.of(context, rootNavigator: true).push<void>(
        MaterialPageRoute(
          fullscreenDialog: true,
          builder: (_) => InformeCrearScreen(
            salidaId: salidaId,
            salidaNombre: existing?.salidaNombre,
            existingInforme: existing,
            onSaved: () => ref.invalidate(informeDetailProvider(salidaId)),
          ),
        ),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Informe')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => AppEmptyState(
            message: 'Error al cargar', error: e),
        data: (informe) => informe == null
            ? _InformeNoCreado(
                canEdit: canEdit,
                onCrear: () => openForm(),
              )
            : _InformeBody(
                informe: informe,
                salidaId: salidaId,
                canValidar: canValidar,
                canEdit: canEdit,
                canGeneratePdf: canGeneratePdf,
                onValidar: () async {
                  await ref
                      .read(informesRepositoryProvider)
                      .validarInforme(salidaId);
                  ref.invalidate(informeDetailProvider(salidaId));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Informe validado')),
                    );
                  }
                },
                onEditar: () => openForm(existing: informe),
              ),
      ),
    );
  }
}

class _InformeNoCreado extends StatelessWidget {
  const _InformeNoCreado({required this.canEdit, required this.onCrear});
  final bool canEdit;
  final VoidCallback onCrear;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.description_outlined,
                size: 48, color: AppColors.mutedFg),
            const SizedBox(height: 16),
            Text('Informe no creado',
                style: AppTextStyles.titleMedium
                    .copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            Text(
              'El jefe de salida aún no ha completado este informe.',
              textAlign: TextAlign.center,
              style:
                  AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
            ),
            if (canEdit) ...[
              const SizedBox(height: 24),
              AppButton(
                label: 'Crear informe',
                fullWidth: true,
                icon: Icons.add,
                onPressed: onCrear,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _InformeBody extends ConsumerStatefulWidget {
  const _InformeBody({
    required this.informe,
    required this.salidaId,
    required this.canValidar,
    required this.canEdit,
    required this.canGeneratePdf,
    required this.onValidar,
    required this.onEditar,
  });
  final Informe informe;
  final String salidaId;
  final bool canValidar;
  final bool canEdit;
  final bool canGeneratePdf;
  final VoidCallback onValidar;
  final VoidCallback onEditar;

  @override
  ConsumerState<_InformeBody> createState() => _InformeBodyState();
}

class _InformeBodyState extends ConsumerState<_InformeBody> {
  bool _pdfLoading = false;

  Future<void> _compartirPdf() async {
    setState(() => _pdfLoading = true);
    try {
      final bytes =
          await ref.read(informesRepositoryProvider).downloadPdf(widget.salidaId);
      await _sharePdfBytes(bytes, widget.salidaId);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al descargar PDF: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _pdfLoading = false);
    }
  }

  Future<void> _generarPdf() async {
    setState(() => _pdfLoading = true);
    try {
      final bytes =
          await ref.read(informesRepositoryProvider).generarPdf(widget.salidaId);
      await _sharePdfBytes(bytes, widget.salidaId);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al generar PDF: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _pdfLoading = false);
    }
  }

  Future<void> _sharePdfBytes(List<int> bytes, String salidaId) async {
    final dir = await getTemporaryDirectory();
    final filename = 'informe-$salidaId.pdf';
    final file = File('${dir.path}/$filename');
    await file.writeAsBytes(bytes);
    await SharePlus.instance.share(
      ShareParams(
          files: [XFile(file.path, mimeType: 'application/pdf', name: filename)]),
    );
  }

  @override
  Widget build(BuildContext context) {
    final informe = widget.informe;
    final estado = informe.estado ?? 'PENDIENTE';
    final validated = estado.toUpperCase() == 'VALIDADO';
    final hasPdf = informe.documentoId != null;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _StatusBanner(estado: estado),
        const SizedBox(height: 16),

        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(informe.salidaNombre,
                  style: AppTextStyles.headlineMedium
                      .copyWith(fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              if (informe.seRealizo != null)
                _BoolRow('Se realizó', informe.seRealizo!),
              if (informe.lograronCumbre != null)
                _BoolRow('Cumbre lograda', informe.lograronCumbre!),
              if (informe.costoTotal != null)
                _TextRow('Costo total', '\$${informe.costoTotal!.toStringAsFixed(2)}'),
              if (informe.costoPorPersona != null)
                _TextRow('Costo por persona',
                    '\$${informe.costoPorPersona!.toStringAsFixed(2)}'),
            ],
          ),
        ),
        const SizedBox(height: 16),

        if (informe.condicionesMeteorologicas != null) ...[
          _Section('Condiciones meteorológicas',
              informe.condicionesMeteorologicas!),
          const SizedBox(height: 16),
        ],

        if (informe.cronica != null) ...[
          _Section('Crónica', informe.cronica!),
          const SizedBox(height: 16),
        ],

        if (informe.observaciones != null) ...[
          _Section('Observaciones', informe.observaciones!),
          const SizedBox(height: 16),
        ],

        if (informe.tramos.isNotEmpty) ...[
          Text('Transporte',
              style: AppTextStyles.titleMedium
                  .copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          ...informe.tramos.map((t) => _TramoCard(tramo: t)),
          const SizedBox(height: 16),
        ],

        if (informe.reconocimientos.isNotEmpty) ...[
          Text('Reconocimientos',
              style: AppTextStyles.titleMedium
                  .copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          ...informe.reconocimientos.map((r) => _ReconocimientoItem(r: r)),
          const SizedBox(height: 16),
        ],

        // ── Botones PDF (solo si validado) ──────────────────────────────
        if (validated) ...[
          if (hasPdf) ...[
            // PDF ya generado: compartir (todos) + regenerar (privilegiados)
            Row(
              children: [
                Expanded(
                  child: AppButton(
                    label: 'Compartir PDF',
                    variant: AppButtonVariant.secondary,
                    icon: Icons.picture_as_pdf_outlined,
                    loading: _pdfLoading,
                    onPressed: _pdfLoading ? null : _compartirPdf,
                  ),
                ),
                if (widget.canGeneratePdf) ...[
                  const SizedBox(width: 8),
                  SizedBox(
                    height: 44,
                    child: OutlinedButton(
                      onPressed: _pdfLoading ? null : _generarPdf,
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                      ),
                      child: _pdfLoading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Tooltip(
                              message: 'Regenerar PDF',
                              child: Icon(Icons.refresh, size: 18),
                            ),
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 12),
          ] else if (widget.canGeneratePdf) ...[
            // Sin PDF todavía: solo privilegiados pueden generarlo
            AppButton(
              label: 'Generar PDF',
              fullWidth: true,
              variant: AppButtonVariant.secondary,
              icon: Icons.picture_as_pdf_outlined,
              loading: _pdfLoading,
              onPressed: _pdfLoading ? null : _generarPdf,
            ),
            const SizedBox(height: 12),
          ],
        ],

        // ── Editar / Validar (solo si no validado) ───────────────────────
        if (widget.canEdit && !validated) ...[
          AppButton(
            label: 'Editar informe',
            fullWidth: true,
            variant: AppButtonVariant.secondary,
            icon: Icons.edit_outlined,
            onPressed: widget.onEditar,
          ),
          const SizedBox(height: 12),
        ],
        if (widget.canValidar && !validated) ...[
          AppButton(
            label: 'Validar informe',
            fullWidth: true,
            icon: Icons.verified_outlined,
            onPressed: widget.onValidar,
          ),
          const SizedBox(height: 16),
        ],
      ],
    );
  }
}

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.estado});
  final String estado;

  @override
  Widget build(BuildContext context) {
    final color = switch (estado.toUpperCase()) {
      'VALIDADO' => AppColors.salidaRealizada,
      'COMPLETADO' => AppColors.salidaEnCurso,
      _ => AppColors.salidaPlanificada,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Icon(Icons.info_outline, color: color, size: 18),
          const SizedBox(width: 8),
          Text('Estado: $estado',
              style: TextStyle(
                  color: color, fontWeight: FontWeight.w600, fontSize: 14)),
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section(this.title, this.content);
  final String title;
  final String content;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title,
              style: AppTextStyles.titleSmall
                  .copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          Text(content,
              style:
                  AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg)),
        ],
      ),
    );
  }
}

class _TextRow extends StatelessWidget {
  const _TextRow(this.label, this.value);
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(children: [
          SizedBox(
            width: 110,
            child: Text(label,
                style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
          ),
          Text(value, style: AppTextStyles.bodyMedium),
        ]),
      );
}

class _BoolRow extends StatelessWidget {
  const _BoolRow(this.label, this.value);
  final String label;
  final bool value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(children: [
        SizedBox(
          width: 110,
          child: Text(label,
              style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
        ),
        Icon(
          value ? Icons.check_circle_outline : Icons.cancel_outlined,
          color: value ? AppColors.salidaRealizada : AppColors.salidaCancelada,
          size: 18,
        ),
      ]),
    );
  }
}

class _TramoCard extends StatelessWidget {
  const _TramoCard({required this.tramo});
  final TramoTransporte tramo;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          const Icon(Icons.directions_car_outlined,
              color: AppColors.mutedFg, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (tramo.origen != null && tramo.destino != null)
                  Text('${tramo.origen} → ${tramo.destino}',
                      style: AppTextStyles.bodySmall),
                if (tramo.tipoTransporte != null)
                  Text(tramo.tipoTransporte!,
                      style: AppTextStyles.labelSmall
                          .copyWith(color: AppColors.mutedFg)),
              ],
            ),
          ),
          if (tramo.costoIndividual != null)
            Text('\$${tramo.costoIndividual!.toStringAsFixed(2)}',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.primary)),
        ],
      ),
    );
  }
}

class _ReconocimientoItem extends StatelessWidget {
  const _ReconocimientoItem({required this.r});
  final Reconocimiento r;

  @override
  Widget build(BuildContext context) {
    final isDestacado = r.tipo.toUpperCase() == 'DESTACADO';
    final color =
        isDestacado ? AppColors.salidaRealizada : AppColors.salidaCancelada;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        isDestacado ? Icons.star_outline : Icons.warning_amber_outlined,
        color: color,
      ),
      title: Text(r.socioNombre, style: AppTextStyles.bodyMedium),
      subtitle:
          r.motivo != null ? Text(r.motivo!, style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)) : null,
      trailing: Text(r.tipo,
          style: TextStyle(
              color: color, fontSize: 11, fontWeight: FontWeight.w600)),
    );
  }
}
