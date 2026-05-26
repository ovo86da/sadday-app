import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_avatar.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../providers/actas_provider.dart';
import 'acta_crear_screen.dart';

class ActaDetailScreen extends ConsumerStatefulWidget {
  const ActaDetailScreen({required this.id, super.key});
  final String id;

  @override
  ConsumerState<ActaDetailScreen> createState() => _ActaDetailScreenState();
}

class _ActaDetailScreenState extends ConsumerState<ActaDetailScreen> {
  bool _pdfLoading = false;

  Future<void> _sharePdfBytes(List<int> bytes) async {
    final dir = await getTemporaryDirectory();
    final filename = 'acta-${widget.id}.pdf';
    final file = File('${dir.path}/$filename');
    await file.writeAsBytes(bytes);
    await SharePlus.instance.share(
      ShareParams(
        files: [XFile(file.path, mimeType: 'application/pdf', name: filename)],
      ),
    );
  }

  Future<void> _generarPdf() async {
    setState(() => _pdfLoading = true);
    try {
      final bytes = await ref.read(actasRepositoryProvider).generarPdf(widget.id);
      ref.invalidate(actaDetailProvider(widget.id));
      await _sharePdfBytes(bytes);
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

  Future<void> _compartirPdf() async {
    setState(() => _pdfLoading = true);
    try {
      final bytes = await ref.read(actasRepositoryProvider).descargarPdf(widget.id);
      await _sharePdfBytes(bytes);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al compartir PDF: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _pdfLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(actaDetailProvider(widget.id));
    final authVal = ref.watch(authNotifierProvider).asData?.value;
    final role = authVal is AuthAuthenticated ? authVal.user.rol : null;
    final isAdminOrSec = role == UserRole.admin || role == UserRole.secretaria;
    final isDirectivo = isAdminOrSec || role == UserRole.directivo;
    final canGeneratePdf = isAdminOrSec;
    final canEdit = isAdminOrSec;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Acta')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => AppEmptyState(
            message: 'Error al cargar', description: e.toString()),
        data: (acta) {
          final tipo = acta.tipo?.toUpperCase() ?? 'SOCIOS';
          final canCompartirPdf = tipo != 'DIRECTIVA' ? true : isDirectivo;
          final df = DateFormat('dd/MM/yyyy', 'es');

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      acta.numero != null
                          ? 'Acta N° ${acta.numero}'
                          : 'Acta sin número',
                      style: AppTextStyles.headlineMedium
                          .copyWith(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    if (acta.fecha != null) _Row('Fecha', df.format(acta.fecha!)),
                    if (acta.tipo != null) _Row('Tipo', acta.tipo!),
                    if (acta.lugar != null) _Row('Lugar', acta.lugar!),
                    _Row('Asistentes', '${acta.totalAsistentes}'),
                    if (acta.presidenteNombre != null)
                      _Row('Presidente', acta.presidenteNombre!),
                    if (acta.secretariaNombre != null)
                      _Row('Secretaria', acta.secretariaNombre!),
                    if (acta.descripcion != null) ...[
                      const SizedBox(height: 12),
                      Text('Descripción',
                          style: AppTextStyles.titleSmall
                              .copyWith(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 6),
                      Text(acta.descripcion!,
                          style: AppTextStyles.bodyMedium
                              .copyWith(color: AppColors.mutedFg)),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 16),

              if (acta.actividadesRealizadas != null) ...[
                _Section('Actividades realizadas', acta.actividadesRealizadas!),
                const SizedBox(height: 16),
              ],
              if (acta.actividadesPorRealizar != null) ...[
                _Section('Actividades por realizar', acta.actividadesPorRealizar!),
                const SizedBox(height: 16),
              ],
              if (acta.acuerdos != null) ...[
                _Section('Acuerdos', acta.acuerdos!),
                const SizedBox(height: 16),
              ],
              if (acta.varios != null) ...[
                _Section('Varios', acta.varios!),
                const SizedBox(height: 16),
              ],
              if (acta.observaciones != null) ...[
                _Section('Observaciones', acta.observaciones!),
                const SizedBox(height: 16),
              ],

              if (acta.asistentes.isNotEmpty) ...[
                Text('Asistentes',
                    style: AppTextStyles.titleMedium
                        .copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 8),
                AppCard(
                  child: Column(
                    children: acta.asistentes
                        .map((a) => ListTile(
                              dense: true,
                              leading: AppAvatar(name: a.nombre, size: 32),
                              title:
                                  Text(a.nombre, style: AppTextStyles.bodyMedium),
                            ))
                        .toList(),
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // PDF ya existe: compartir (según rol) + regenerar (canGeneratePdf)
              if (acta.tienePdf && canCompartirPdf) ...[
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
                    if (canGeneratePdf) ...[
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
              ],

              // Sin PDF: generar (solo canGeneratePdf)
              if (!acta.tienePdf && canGeneratePdf) ...[
                AppButton(
                  label: 'Generar PDF',
                  fullWidth: true,
                  icon: Icons.picture_as_pdf_outlined,
                  loading: _pdfLoading,
                  onPressed: _pdfLoading ? null : _generarPdf,
                ),
                const SizedBox(height: 12),
              ],

              if (canEdit) ...[
                AppButton(
                  label: 'Editar acta',
                  fullWidth: true,
                  variant: AppButtonVariant.secondary,
                  icon: Icons.edit_outlined,
                  onPressed: () {
                    Navigator.of(context, rootNavigator: true).push<void>(
                      MaterialPageRoute(
                        fullscreenDialog: true,
                        builder: (_) => ActaCrearScreen(
                          tipo: acta.tipo ?? 'SOCIOS',
                          existingId: widget.id,
                          onSaved: () =>
                              ref.invalidate(actaDetailProvider(widget.id)),
                        ),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 16),
              ],
            ],
          );
        },
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
              style:
                  AppTextStyles.titleSmall.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          Text(content,
              style:
                  AppTextStyles.bodyMedium.copyWith(color: AppColors.mutedFg)),
        ],
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
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(children: [
          SizedBox(
            width: 90,
            child: Text(label,
                style:
                    AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg)),
          ),
          Expanded(child: Text(value, style: AppTextStyles.bodyMedium)),
        ]),
      );
}
