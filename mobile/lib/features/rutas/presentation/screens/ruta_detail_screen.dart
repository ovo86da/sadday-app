import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
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
import '../../domain/models/ruta_model.dart';
import '../providers/rutas_provider.dart';

class RutaDetailScreen extends ConsumerStatefulWidget {
  const RutaDetailScreen({required this.id, super.key});
  final int id;

  @override
  ConsumerState<RutaDetailScreen> createState() => _RutaDetailScreenState();
}

class _RutaDetailScreenState extends ConsumerState<RutaDetailScreen> {
  bool _saving = false;

  // ── Documentos de la ruta (FR-025, segunda mitad) ──────────────────────────

  String _tipoFichero = 'TRACK_GPX';
  bool _subiendo = false;

  /// El socio que mira, para saber cual de los documentos es «el mio».
  String? get _miSocioId {
    final auth = ref.read(authNotifierProvider).asData?.value;
    return auth is AuthAuthenticated ? auth.user.socioId : null;
  }

  /// Descarga y abre el fichero con el selector del sistema.
  ///
  /// Pasa por dio, no por una URL abierta en el navegador: una navegacion no
  /// lleva la cabecera Authorization y el backend responde 401. Y acaba en el
  /// menu de compartir a proposito, porque un GPX que no se puede abrir en
  /// GaiaGPS o Komoot no sirve de nada.
  Future<void> _descargarDocumento(RutaDocumento doc) async {
    try {
      final bytes = await ref
          .read(rutasRepositoryProvider)
          .descargarDocumentoRuta(widget.id, doc.id);
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/${doc.filename}');
      await file.writeAsBytes(bytes);
      if (!mounted) return;
      await SharePlus.instance.share(ShareParams(
        files: [
          XFile(file.path, mimeType: doc.contentType, name: doc.filename),
        ],
        text: doc.tipo == 'TRACK_GPX'
            ? 'Abrelo en GaiaGPS, CalTopo o Komoot para verlo sobre el mapa.'
            : null,
      ));
    } catch (e) {
      if (mounted) _avisar('No se pudo descargar: ${unwrapDio(e)}');
    }
  }

  Future<void> _eliminarDocumento(RutaDocumento doc) async {
    // Borrar no se deshace, asi que se pregunta. El permiso lo decide el
    // servidor: `puedoEliminar` solo oculta el boton.
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Eliminar documento'),
        content: Text('Se borrara "${doc.filename}" y no se puede deshacer.'
            '${doc.confirmaciones > 0 ? "\n\nTiene ${doc.confirmaciones} confirmacion(es), que tambien se pierden." : ""}'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancelar')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Eliminar')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref
          .read(rutasRepositoryProvider)
          .eliminarDocumentoRuta(widget.id, doc.id);
      ref.invalidate(rutaDetailProvider(widget.id));
      if (mounted) _avisar('Documento eliminado');
    } catch (e) {
      if (mounted) _avisar('No se pudo eliminar: ${unwrapDio(e)}');
    }
  }

  Future<void> _alternarConfirmacion(RutaDocumento doc) async {
    try {
      final repo = ref.read(rutasRepositoryProvider);
      if (doc.confirmadoPorMi) {
        await repo.retirarConfirmacionRuta(widget.id, doc.id);
      } else {
        await repo.confirmarDocumentoRuta(widget.id, doc.id);
      }
      ref.invalidate(rutaDetailProvider(widget.id));
    } catch (e) {
      if (mounted) _avisar('No se pudo registrar: ${unwrapDio(e)}');
    }
  }

  /// Elige un fichero y lo sube, preguntando antes si ya hay uno propio.
  Future<void> _adjuntarDocumento(Ruta r) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: _tipoFichero == 'TRACK_GPX'
          ? const ['gpx']
          : const ['pdf', 'doc', 'docx', 'xls', 'xlsx'],
    );
    final ruta = result?.files.single.path;
    if (ruta == null) return;

    // El tamano se comprueba aqui y no tras subir: mandar 11 MB para que el
    // servidor los rechace corta la conexion y el motivo se pierde.
    final bytes = await File(ruta).length();
    if (bytes > 10 * 1024 * 1024) {
      _avisar('El fichero pesa ${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB '
          'y el maximo son 10 MB.');
      return;
    }

    // Entre elegir el fichero y leer su tamano hay dos awaits, y la pantalla
    // puede haberse cerrado: sin esta guarda el `context` del dialogo ya no
    // vale.
    if (!mounted) return;

    final mios = r.documentos
        .where((d) => d.tipo == _tipoFichero && d.subidoPorId == _miSocioId)
        .toList()
      ..sort((a, b) => (b.createdAt ?? '').compareTo(a.createdAt ?? ''));

    String? reemplazarId;
    if (mios.isNotEmpty) {
      // Tres salidas, no dos: reemplazar, añadir otro, o salir. Un
      // Aceptar/Cancelar obligaria a adivinar cual es cual.
      final enElLimite =
          mios.length >= (RutaDocumento.limitePorTipo[_tipoFichero] ?? 3);
      final decision = await showDialog<String>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text('Ya tienes ${RutaDocumento.etiquetaTipo(_tipoFichero).toLowerCase()} aqui'),
          content: Text(
            'Subiste "${mios.first.filename}".'
            '${enElLimite ? " Has llegado al maximo de ${RutaDocumento.limitePorTipo[_tipoFichero]} por socio, "
                "asi que para subir este tienes que reemplazar el anterior." : " Puedes reemplazarlo o conservar los dos."}'
            '${mios.first.confirmaciones > 0 ? "\n\nOjo: el anterior tiene ${mios.first.confirmaciones} voto(s) y los perdera al reemplazarlo." : ""}',
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, 'cancelar'),
                child: const Text('Cancelar')),
            if (!enElLimite)
              TextButton(
                  onPressed: () => Navigator.pop(ctx, 'anadir'),
                  child: const Text('Anadir nuevo')),
            TextButton(
                onPressed: () => Navigator.pop(ctx, 'reemplazar'),
                child: const Text('Reemplazar')),
          ],
        ),
      );
      if (decision == null || decision == 'cancelar') return;
      if (decision == 'reemplazar') reemplazarId = mios.first.id;
    }

    setState(() => _subiendo = true);
    try {
      await ref.read(rutasRepositoryProvider).subirDocumentoRuta(
          widget.id, ruta, _tipoFichero, reemplazarId: reemplazarId);
      ref.invalidate(rutaDetailProvider(widget.id));
      if (mounted) _avisar('Documento subido');
    } catch (e) {
      if (mounted) _avisar('No se pudo subir: ${unwrapDio(e)}');
    } finally {
      if (mounted) setState(() => _subiendo = false);
    }
  }

  void _avisar(String mensaje) => ScaffoldMessenger.of(context)
      .showSnackBar(SnackBar(content: Text(mensaje)));

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
                  if (r.desnivel != null) _Row('Desnivel', r.desnivel!),
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

              // ── Documentos y tracks (FR-022 en la web, FR-025 aqui) ────────
              const SizedBox(height: 12),
              AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Documentos y tracks',
                        style: AppTextStyles.titleSmall
                            .copyWith(fontWeight: FontWeight.w600)),
                    const SizedBox(height: 4),
                    // La seccion se muestra SIEMPRE, tambien en rutas que no
                    // exigen permisos: en la web estuvo condicionada a
                    // `requierePermisos` y por eso un track no se veia en la
                    // mayoria de rutas.
                    Text(
                      'Cualquier socio puede subir el track que grabo, un mapa '
                      'o la plantilla de permiso. Una misma ruta puede tener '
                      'varios de distintas personas.',
                      style: AppTextStyles.bodySmall
                          .copyWith(color: AppColors.mutedFg),
                    ),
                    const SizedBox(height: 12),

                    Row(children: [
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          initialValue: _tipoFichero,
                          decoration: const InputDecoration(
                            labelText: 'Tipo', isDense: true,
                            border: OutlineInputBorder(),
                          ),
                          items: const [
                            DropdownMenuItem(value: 'TRACK_GPX', child: Text('Track GPX')),
                            DropdownMenuItem(value: 'MAPA', child: Text('Mapa')),
                            DropdownMenuItem(value: 'PERMISO', child: Text('Plantilla de permiso')),
                            DropdownMenuItem(value: 'OTRO', child: Text('Otro')),
                          ],
                          onChanged: (v) =>
                              setState(() => _tipoFichero = v ?? 'TRACK_GPX'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      AppButton(
                        label: 'Adjuntar',
                        variant: AppButtonVariant.secondary,
                        loading: _subiendo,
                        onPressed: () => _adjuntarDocumento(r),
                      ),
                    ]),

                    if (r.documentos.isEmpty) ...[
                      const SizedBox(height: 12),
                      Text('No hay documentos todavia.',
                          style: AppTextStyles.bodySmall
                              .copyWith(color: AppColors.mutedFg)),
                    ] else
                      for (final tipo in RutaDocumento.ordenTipos)
                        if (r.documentos.any((d) => d.tipo == tipo)) ...[
                          const SizedBox(height: 14),
                          Text(RutaDocumento.etiquetaTipo(tipo),
                              style: AppTextStyles.labelSmall
                                  .copyWith(color: AppColors.mutedFg)),
                          for (final doc
                              in r.documentos.where((d) => d.tipo == tipo))
                            Padding(
                              padding: const EdgeInsets.only(top: 8),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(children: [
                                    const Icon(Icons.insert_drive_file_outlined, size: 18),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(doc.filename,
                                              style: AppTextStyles.bodySmall,
                                              overflow: TextOverflow.ellipsis),
                                          Text(
                                            '${doc.tamanoLegible}'
                                            '${doc.subidoPorNombre != null ? " · ${doc.subidoPorNombre}" : ""}',
                                            style: AppTextStyles.bodySmall
                                                .copyWith(color: AppColors.mutedFg),
                                          ),
                                        ],
                                      ),
                                    ),
                                    IconButton(
                                      icon: const Icon(Icons.download_outlined, size: 20),
                                      tooltip: 'Descargar',
                                      onPressed: () => _descargarDocumento(doc),
                                    ),
                                    // Solo si el servidor dice que se puede. La
                                    // comprobacion de verdad esta en el
                                    // endpoint; esto es cortesia.
                                    if (doc.puedoEliminar)
                                      IconButton(
                                        icon: const Icon(Icons.delete_outline, size: 20),
                                        tooltip: 'Eliminar',
                                        onPressed: () => _eliminarDocumento(doc),
                                      ),
                                  ]),
                                  // El contador necesita texto: un numero a
                                  // secas no dice que se esta confirmando.
                                  Padding(
                                    padding: const EdgeInsets.only(left: 26),
                                    child: InkWell(
                                      onTap: () => _alternarConfirmacion(doc),
                                      child: Row(children: [
                                        Icon(
                                          doc.confirmadoPorMi
                                              ? Icons.check_circle
                                              : Icons.check_circle_outline,
                                          size: 16,
                                          color: doc.confirmadoPorMi
                                              ? AppColors.primary
                                              : AppColors.mutedFg,
                                        ),
                                        const SizedBox(width: 6),
                                        Expanded(
                                          child: Text(
                                            doc.confirmaciones == 0
                                                ? 'Nadie ha confirmado todavia que sirva'
                                                : '${doc.confirmaciones} socio(s) confirman que sirve'
                                                  '${doc.confirmadoPorMi ? " (incluido tu)" : ""}',
                                            style: AppTextStyles.bodySmall
                                                .copyWith(color: AppColors.mutedFg),
                                          ),
                                        ),
                                      ]),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                        ],
                  ],
                ),
              ),

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
