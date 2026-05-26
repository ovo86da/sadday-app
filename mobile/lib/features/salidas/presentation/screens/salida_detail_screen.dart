import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import '../../../../core/api/app_exception.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_avatar.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_empty_state.dart';
import '../../../../core/widgets/app_loading_overlay.dart';
import '../../../../core/widgets/app_status_badge.dart';
import '../../../socios/domain/models/socio_model.dart';
import '../../domain/models/salida_model.dart';
import '../providers/salidas_provider.dart';
import 'salidas_screen.dart';

class SalidaDetailScreen extends ConsumerStatefulWidget {
  const SalidaDetailScreen({required this.id, super.key});
  final String id;

  @override
  ConsumerState<SalidaDetailScreen> createState() =>
      _SalidaDetailScreenState();
}

class _SalidaDetailScreenState extends ConsumerState<SalidaDetailScreen> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final asyncSalida = ref.watch(salidaDetailProvider(widget.id));
    final authVal = ref.watch(authNotifierProvider).asData?.value;
    final user = authVal is AuthAuthenticated ? authVal.user : null;
    final role = user?.rol;
    final isPrivileged = role == UserRole.admin ||
        role == UserRole.secretaria ||
        role == UserRole.directivo;
    final salida = asyncSalida.asData?.value;
    final dignidadesLookup = ref
            .watch(salidaLookupsProvider)
            .asData
            ?.value
            .dignidades ??
        const <Dignidad>[];
    final clasificaciones =
        ref.watch(clasificacionesProvider).asData?.value ?? const <Clasificacion>[];

    final acciones =
        salida != null && isPrivileged ? _accionesDisponibles(salida) : const <String>[];

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Detalle de Salida'),
        actions: [
          if (acciones.isNotEmpty)
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert),
              color: AppColors.sidebar,
              onSelected: (v) => _onAction(v, salida!),
              itemBuilder: (_) => [
                for (final a in acciones)
                  PopupMenuItem<String>(value: a, child: _menuRow(a)),
              ],
            ),
        ],
      ),
      body: AppLoadingOverlay(
        isLoading: _busy,
        child: asyncSalida.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => AppEmptyState(
            message: 'Error al cargar la salida',
            description: unwrapDio(e).toString(),
            icon: Icons.error_outline,
          ),
          data: (s) =>
              _buildBody(s, user, isPrivileged, dignidadesLookup, clasificaciones),
        ),
      ),
    );
  }

  // ── Acciones de gestión ───────────────────────────────────────────────────

  List<String> _accionesDisponibles(SalidaDetalle salida) {
    final estado = salida.estado.toUpperCase();
    return [
      if (estado == 'PLANIFICADA') 'editar',
      if (estado != 'REALIZADA' && estado != 'CANCELADA') 'cancelar',
      if (estado != 'REALIZADA') 'eliminar',
    ];
  }

  Widget _menuRow(String accion) {
    final (icon, label, destructive) = switch (accion) {
      'editar' => (Icons.edit_outlined, 'Editar', false),
      'cancelar' => (Icons.cancel_outlined, 'Cancelar salida', false),
      _ => (Icons.delete_outline, 'Eliminar salida', true),
    };
    final color = destructive ? AppColors.destructive : AppColors.foreground;
    return Row(children: [
      Icon(icon, size: 18, color: color),
      const SizedBox(width: 10),
      Text(label, style: TextStyle(color: color)),
    ]);
  }

  void _onAction(String accion, SalidaDetalle salida) {
    switch (accion) {
      case 'editar':
        _openEditForm(salida);
      case 'cancelar':
        _cancelarSalida(salida);
      case 'eliminar':
        _eliminarSalida(salida);
    }
  }

  void _openEditForm(SalidaDetalle salida) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => SalidaFormSheet(
        salida: salida,
        onSaved: () => ref.invalidate(salidaDetailProvider(widget.id)),
      ),
    );
  }

  Future<void> _cancelarSalida(SalidaDetalle salida) async {
    final motivo = await _promptMotivo(
      title: 'Cancelar salida',
      hint: 'Motivo de la cancelación',
      confirmLabel: 'Cancelar salida',
    );
    if (motivo == null) return;
    await _run(
      () => ref.read(salidasRepositoryProvider).cancelarSalida(salida.id, motivo),
      success: 'Salida cancelada',
    );
  }

  Future<void> _eliminarSalida(SalidaDetalle salida) async {
    final motivo = await _promptMotivo(
      title: 'Eliminar salida',
      hint: 'Motivo de la eliminación',
      confirmLabel: 'Eliminar salida',
      destructive: true,
    );
    if (motivo == null) return;
    await _run(
      () => ref.read(salidasRepositoryProvider).eliminarSalida(salida.id, motivo),
      success: 'Salida eliminada',
      popOnSuccess: true,
    );
  }

  // ── Inscripción propia ────────────────────────────────────────────────────

  Future<void> _inscribirme(
    SalidaDetalle salida,
    UserModel user,
    List<Clasificacion> clasificaciones,
  ) async {
    bool pendiente = false;
    final nivelMinimo = salida.nivelMinimo;
    if (nivelMinimo != null) {
      final nivelMap = {for (final c in clasificaciones) c.nombre: c.nivel};
      final userNivel = nivelMap[user.nivelTecnico];
      final minNivel = nivelMap[nivelMinimo];
      final insuficiente =
          userNivel == null || minNivel == null || userNivel < minNivel;
      if (insuficiente) {
        final confirm = await _showLevelWarning(nivelMinimo);
        if (confirm != true) return;
        pendiente = true;
      }
    }
    await _run(
      () => ref
          .read(salidasRepositoryProvider)
          .inscribirse(salida.id, socioId: user.socioId),
      success: pendiente
          ? 'Inscripción registrada — pendiente de aprobación'
          : '¡Inscripción realizada!',
    );
  }

  Future<bool?> _showLevelWarning(String nivelMinimo) {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.sidebar,
        title: Row(children: [
          const Icon(Icons.warning_amber_outlined,
              color: AppColors.salidaPlanificada, size: 22),
          const SizedBox(width: 8),
          const Expanded(
              child: Text('Requisitos no cumplidos',
                  style: TextStyle(color: AppColors.foreground, fontSize: 16))),
        ]),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'No cumples con los requisitos técnicos para esta salida '
              '(nivel mínimo: $nivelMinimo).',
              style: const TextStyle(color: AppColors.mutedFg),
            ),
            const SizedBox(height: 12),
            const Text(
              '¿Deseas continuar y solicitar que el Jefe de Salida y el '
              'Jefe de Montaña autoricen tu inscripción?',
              style: TextStyle(color: AppColors.foreground),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Volver atrás'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Continuar y solicitar aprobación'),
          ),
        ],
      ),
    );
  }

  Future<void> _cancelarInscripcion(
      SalidaDetalle salida, Participante mine) async {
    final isPendiente =
        mine.estadoInscripcion.toUpperCase() == 'PENDIENTE_APROBACION';
    final ok = await _promptConfirm(
      title: isPendiente ? 'Cancelar solicitud' : 'Cancelar inscripción',
      message: isPendiente
          ? '¿Cancelar tu solicitud de inscripción en esta salida?'
          : '¿Cancelar tu inscripción en esta salida?',
      confirmLabel: isPendiente ? 'Cancelar solicitud' : 'Cancelar inscripción',
    );
    if (ok != true) return;
    await _run(
      () => ref
          .read(salidasRepositoryProvider)
          .cancelarInscripcion(salida.id, mine.inscripcionId),
      success: isPendiente ? 'Solicitud cancelada' : 'Inscripción cancelada',
    );
  }

  // ── Riesgo de inscripción ─────────────────────────────────────────────────

  Future<void> _decidirRiesgo(
      String salidaId, int participanteId, bool aprobar, String motivo) async {
    await _run(
      () => ref
          .read(salidasRepositoryProvider)
          .decidirRiesgo(salidaId, participanteId, aprobar, motivo),
      success: aprobar ? 'Aprobación registrada' : 'Inscripción negada',
    );
  }

  Future<void> _revocarAprobacion(
      String salidaId, int participanteId) async {
    await _run(
      () => ref
          .read(salidasRepositoryProvider)
          .revocarAprobacion(salidaId, participanteId),
      success: 'Aprobación revocada',
    );
  }

  // ── Cerrar / abrir inscripciones ─────────────────────────────────────────

  Future<void> _toggleCerrarInscripciones() async {
    setState(() => _busy = true);
    try {
      final cerradas = await ref
          .read(salidasRepositoryProvider)
          .toggleCerrarInscripciones(widget.id);
      if (!mounted) return;
      ref.invalidate(salidaDetailProvider(widget.id));
      _snack(cerradas
          ? 'Inscripciones cerradas'
          : 'Inscripciones abiertas nuevamente');
    } catch (e) {
      if (mounted) _snack(unwrapDio(e).toString(), error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  // ── Dignidades / Jefe de salida ───────────────────────────────────────────

  Future<void> _designarJefe(String salidaId, Participante p) async {
    await _run(
      () => ref
          .read(salidasRepositoryProvider)
          .designarJefeSalida(salidaId, p.inscripcionId),
      success: '${p.nombre} designado como Jefe de Salida',
    );
  }

  Future<void> _agregarDignidad(
      String salidaId, Participante p, int dignidadId, String nombre) async {
    await _run(
      () => ref
          .read(salidasRepositoryProvider)
          .agregarDignidad(salidaId, p.inscripcionId, dignidadId),
      success: 'Dignidad "$nombre" asignada',
    );
  }

  Future<void> _quitarDignidad(
      String salidaId, Participante p, int asignadaId, String nombre) async {
    await _run(
      () => ref
          .read(salidasRepositoryProvider)
          .eliminarDignidad(salidaId, p.inscripcionId, asignadaId),
      success: 'Dignidad "$nombre" removida',
    );
  }

  // ── Descarga CSV participantes ────────────────────────────────────────────

  Future<void> _descargarParticipantes(SalidaDetalle salida) async {
    try {
      final encabezado = ['Nombre', 'Nivel técnico', 'Estado inscripción'];
      final filas = salida.participantes.map((p) => [
            _csvCell(p.nombre),
            _csvCell(p.nivelTecnico ?? ''),
            _csvCell(p.estadoInscripcion),
          ]);
      final csv = [
        encabezado.map(_csvCell).join(','),
        ...filas.map((f) => f.join(','))
      ].join('\n');
      final dir = await getTemporaryDirectory();
      final nombre =
          salida.nombre.replaceAll(RegExp(r'[^a-zA-Z0-9_\-]'), '_');
      final file = File('${dir.path}/participantes_$nombre.csv');
      await file.writeAsString('\u{FEFF}$csv', flush: true);
      await SharePlus.instance.share(ShareParams(
        files: [XFile(file.path, mimeType: 'text/csv')],
        subject: 'Participantes — ${salida.nombre}',
      ));
    } catch (e) {
      if (mounted) _snack('Error al generar el archivo', error: true);
    }
  }

  static String _csvCell(String v) => '"${v.replaceAll('"', '""')}"';

  // ── Helpers ───────────────────────────────────────────────────────────────

  Future<void> _run(
    Future<void> Function() action, {
    required String success,
    bool popOnSuccess = false,
  }) async {
    setState(() => _busy = true);
    try {
      await action();
      if (!mounted) return;
      ref.invalidate(salidaDetailProvider(widget.id));
      _snack(success);
      if (popOnSuccess && context.canPop()) context.pop();
    } catch (e) {
      if (mounted) _snack(unwrapDio(e).toString(), error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _snack(String msg, {bool error = false}) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(
        content: Text(msg),
        backgroundColor: error ? AppColors.destructive : null,
      ));
  }

  Future<bool?> _promptConfirm({
    required String title,
    required String message,
    required String confirmLabel,
  }) {
    return showDialog<bool>(
      context: context,
      builder: (dc) => AlertDialog(
        backgroundColor: AppColors.sidebar,
        title: Text(title,
            style: const TextStyle(color: AppColors.foreground)),
        content: Text(message,
            style: const TextStyle(color: AppColors.mutedFg)),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dc, false),
              child: const Text('Volver')),
          TextButton(
              onPressed: () => Navigator.pop(dc, true),
              child: Text(confirmLabel)),
        ],
      ),
    );
  }

  Future<String?> _promptMotivo({
    required String title,
    required String hint,
    required String confirmLabel,
    bool destructive = false,
  }) {
    final controller = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (dc) => StatefulBuilder(
        builder: (dc, setLocal) {
          final enabled = controller.text.trim().isNotEmpty;
          return AlertDialog(
            backgroundColor: AppColors.sidebar,
            title: Text(title,
                style: const TextStyle(color: AppColors.foreground)),
            content: TextField(
              controller: controller,
              maxLength: 500,
              minLines: 2,
              maxLines: 4,
              autofocus: true,
              style: const TextStyle(color: AppColors.foreground),
              onChanged: (_) => setLocal(() {}),
              decoration: InputDecoration(hintText: hint),
            ),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(dc),
                  child: const Text('Volver')),
              TextButton(
                onPressed: enabled
                    ? () => Navigator.pop(dc, controller.text.trim())
                    : null,
                child: Text(confirmLabel,
                    style: TextStyle(
                        color:
                            destructive ? AppColors.destructive : null)),
              ),
            ],
          );
        },
      ),
    );
  }

  // ── Body ──────────────────────────────────────────────────────────────────

  Widget _buildBody(
    SalidaDetalle salida,
    UserModel? user,
    bool isPrivileged,
    List<Dignidad> dignidadesLookup,
    List<Clasificacion> clasificaciones,
  ) {
    final df = DateFormat('dd/MM/yyyy HH:mm', 'es');
    final status = SalidaStatusX.fromString(salida.estado);
    final estado = salida.estado.toUpperCase();
    final canEdit =
        isPrivileged && (estado == 'PLANIFICADA' || estado == 'EN_CURSO');
    final hayJefe = salida.participantes.any((p) => p.esJefe);
    final dignidadesBase =
        dignidadesLookup.where((d) => d.nombre != 'Jefe de Salida').toList();

    final esJefeMontana = user != null &&
        (user.rol == UserRole.admin ||
            (user.rol == UserRole.directivo && user.esJefeMontana));
    final esJefeSalidaPropio =
        salida.participantes.any((p) => p.esJefe && p.socioId == user?.socioId);

    // Participantes que necesitan MI aprobación de riesgo
    final misPendientes = salida.participantes.where((p) {
      if (p.estadoInscripcion.toUpperCase() != 'PENDIENTE_APROBACION') {
        return false;
      }
      if (esJefeMontana && !p.riesgoAprobadoPorDirectivo) return true;
      if (esJefeSalidaPropio && !p.riesgoAprobadoPorJefe) return true;
      return false;
    }).toList();

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ── Header ────────────────────────────────────────────────────────
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                Expanded(
                  child: Text(salida.nombre,
                      style: AppTextStyles.headlineMedium
                          .copyWith(fontWeight: FontWeight.bold)),
                ),
                AppStatusBadge(status: status),
              ]),
              const SizedBox(height: 12),
              if (salida.montanaNombre != null)
                _InfoRow(
                    icon: Icons.landscape_outlined,
                    label: salida.montanaNombre!),
              if (salida.rutaNombre != null)
                _InfoRow(
                    icon: Icons.route_outlined, label: salida.rutaNombre!),
              if (salida.fechaInicio != null)
                _InfoRow(
                    icon: Icons.calendar_today_outlined,
                    label: 'Inicio: ${df.format(salida.fechaInicio!)}'),
              if (salida.fechaFin != null)
                _InfoRow(
                    icon: Icons.event_outlined,
                    label: 'Fin: ${df.format(salida.fechaFin!)}'),
              if (salida.horaEncuentro != null)
                _InfoRow(
                    icon: Icons.access_time,
                    label: 'Encuentro: ${salida.horaEncuentro}'),
              if (salida.nivelMinimo != null)
                _InfoRow(
                    icon: Icons.signal_cellular_alt_outlined,
                    label: 'Nivel mínimo: ${salida.nivelMinimo}'),
              if (salida.capacidadMaxima != null)
                _InfoRow(
                    icon: Icons.people_outline,
                    label:
                        '${salida.totalInscritos ?? 0} / ${salida.capacidadMaxima} inscritos'),
              if (salida.inscripcionesCerradas)
                _InfoRow(
                    icon: Icons.lock_outline,
                    label: 'Inscripciones cerradas',
                    color: AppColors.destructive),
              if (salida.jefe != null)
                _InfoRow(
                    icon: Icons.person_pin_outlined,
                    label: 'Jefe: ${salida.jefe!.nombre}'),
            ],
          ),
        ),
        const SizedBox(height: 16),

        if (salida.rutaId != null) ...[
          AppButton(
            label: 'Ver recomendaciones',
            variant: AppButtonVariant.secondary,
            icon: Icons.insights_outlined,
            fullWidth: true,
            onPressed: () =>
                context.push('/planificador?rutaId=${salida.rutaId}'),
          ),
          const SizedBox(height: 16),
        ],

        // ── Cancelación ───────────────────────────────────────────────────
        if (estado == 'CANCELADA' && salida.motivoCancelacion != null) ...[
          AppCard(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.cancel_outlined,
                    color: AppColors.salidaCancelada, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Salida cancelada',
                          style: AppTextStyles.bodyMedium.copyWith(
                              fontWeight: FontWeight.w600,
                              color: AppColors.salidaCancelada)),
                      const SizedBox(height: 2),
                      Text(salida.motivoCancelacion!,
                          style: AppTextStyles.bodySmall
                              .copyWith(color: AppColors.mutedFg)),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],

        // ── Banner Jefe de Salida (cerrar inscripciones) ──────────────────
        if (esJefeSalidaPropio &&
            (estado == 'PLANIFICADA' || estado == 'EN_CURSO')) ...[
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: AppColors.salidaPlanificada.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color: AppColors.salidaPlanificada.withValues(alpha: 0.35)),
            ),
            child: Row(children: [
              const Icon(Icons.star_outline,
                  color: AppColors.salidaPlanificada, size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text('Eres Jefe de Salida',
                    style: AppTextStyles.bodyMedium.copyWith(
                        color: AppColors.salidaPlanificada,
                        fontWeight: FontWeight.w600)),
              ),
              const SizedBox(width: 8),
              OutlinedButton.icon(
                onPressed: _toggleCerrarInscripciones,
                style: OutlinedButton.styleFrom(
                  foregroundColor: salida.inscripcionesCerradas
                      ? AppColors.salidaRealizada
                      : AppColors.destructive,
                  side: BorderSide(
                      color: salida.inscripcionesCerradas
                          ? AppColors.salidaRealizada
                          : AppColors.destructive),
                  padding: const EdgeInsets.symmetric(
                      horizontal: 10, vertical: 6),
                  textStyle: const TextStyle(fontSize: 12),
                ),
                icon: Icon(
                  salida.inscripcionesCerradas
                      ? Icons.lock_open_outlined
                      : Icons.lock_outline,
                  size: 14,
                ),
                label: Text(salida.inscripcionesCerradas
                    ? 'Abrir inscripciones'
                    : 'Cerrar inscripciones'),
              ),
            ]),
          ),
          const SizedBox(height: 16),
        ],

        // ── Banner aprobaciones pendientes para JM o JS ───────────────────
        if (misPendientes.isNotEmpty) ...[
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: AppColors.salidaPlanificada.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color:
                      AppColors.salidaPlanificada.withValues(alpha: 0.35)),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.warning_amber_outlined,
                    color: AppColors.salidaPlanificada, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        misPendientes.length == 1
                            ? '1 inscripción requiere tu aprobación'
                            : '${misPendientes.length} inscripciones requieren tu aprobación',
                        style: AppTextStyles.bodyMedium.copyWith(
                            color: AppColors.salidaPlanificada,
                            fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Estos socios tienen nivel insuficiente. '
                        'Desplázate a la lista de participantes para decidir.',
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],

        // ── Panel inscripción propia ──────────────────────────────────────
        _InscripcionPanel(
          salida: salida,
          user: user,
          esJefeSalidaPropio: esJefeSalidaPropio,
          isPrivileged: isPrivileged,
          onInscribirme: user == null
              ? null
              : () => _inscribirme(salida, user, clasificaciones),
          onCancelar: (mine) => _cancelarInscripcion(salida, mine),
        ),

        // ── Descripción ───────────────────────────────────────────────────
        if (salida.descripcion != null) ...[
          const SizedBox(height: 16),
          AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Descripción',
                    style: AppTextStyles.titleMedium
                        .copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 8),
                Text(salida.descripcion!,
                    style: AppTextStyles.bodyMedium
                        .copyWith(color: AppColors.mutedFg)),
              ],
            ),
          ),
        ],

        // ── Participantes ─────────────────────────────────────────────────
        const SizedBox(height: 16),
        Row(children: [
          Expanded(
            child: Text(
              'Participantes (${salida.participantes.length})',
              style: AppTextStyles.titleMedium
                  .copyWith(fontWeight: FontWeight.w600),
            ),
          ),
          if ((isPrivileged || esJefeSalidaPropio) &&
              salida.participantes.isNotEmpty)
            TextButton.icon(
              onPressed: () => _descargarParticipantes(salida),
              icon: const Icon(Icons.download_outlined, size: 18),
              label: const Text('Descargar'),
              style: TextButton.styleFrom(
                foregroundColor: AppColors.mutedFg,
                padding: const EdgeInsets.symmetric(horizontal: 8),
              ),
            ),
        ]),
        const SizedBox(height: 8),
        if (salida.participantes.isEmpty)
          const AppEmptyState(
              message: 'Sin participantes', icon: Icons.people_outline)
        else
          ...salida.participantes.map((p) {
            final esElegible =
                p.estadoInscripcion.toUpperCase() == 'INSCRITO' ||
                    p.estadoInscripcion.toUpperCase() == 'CONFIRMADO';
            final asignadosIds = p.dignidades.map((d) => d.id).toSet();
            final disponibles = dignidadesBase
                .where((d) => !asignadosIds.contains(d.id))
                .toList();

            final canJMDecide = esJefeMontana &&
                p.nivelInsuficiente &&
                !p.riesgoAprobadoPorDirectivo &&
                p.estadoInscripcion.toUpperCase() == 'PENDIENTE_APROBACION';
            final canJSDecide = esJefeSalidaPropio &&
                p.nivelInsuficiente &&
                !p.riesgoAprobadoPorJefe &&
                p.estadoInscripcion.toUpperCase() == 'PENDIENTE_APROBACION';
            final canRevoke = p.nivelInsuficiente &&
                ((esJefeMontana && p.riesgoAprobadoPorDirectivo) ||
                    (esJefeSalidaPropio && p.riesgoAprobadoPorJefe));

            return _ParticipanteItem(
              p: p,
              canEdit: canEdit,
              hayJefe: hayJefe,
              dignidadesDisponibles: esElegible ? disponibles : const [],
              esJefeMontana: esJefeMontana,
              esJefeSalidaPropio: esJefeSalidaPropio,
              onDesignarJefe:
                  (canEdit && esElegible && !p.esJefe && !hayJefe)
                      ? () => _designarJefe(salida.id, p)
                      : null,
              onAgregarDignidad:
                  (canEdit && esElegible && disponibles.isNotEmpty)
                      ? (id, nombre) =>
                          _agregarDignidad(salida.id, p, id, nombre)
                      : null,
              onQuitarDignidad: canEdit
                  ? (asignadaId, nombre) =>
                      _quitarDignidad(salida.id, p, asignadaId, nombre)
                  : null,
              onDecidirRiesgo: (canJMDecide || canJSDecide)
                  ? (aprobar, motivo) =>
                      _decidirRiesgo(salida.id, p.inscripcionId, aprobar, motivo)
                  : null,
              onRevocarAprobacion: canRevoke
                  ? () => _revocarAprobacion(salida.id, p.inscripcionId)
                  : null,
            );
          }),
        const SizedBox(height: 24),
      ],
    );
  }
}

// ── Panel de inscripción propia ──────────────────────────────────────────────

class _InscripcionPanel extends StatelessWidget {
  const _InscripcionPanel({
    required this.salida,
    required this.user,
    required this.esJefeSalidaPropio,
    required this.isPrivileged,
    required this.onInscribirme,
    required this.onCancelar,
  });

  final SalidaDetalle salida;
  final UserModel? user;
  final bool esJefeSalidaPropio;
  final bool isPrivileged;
  final VoidCallback? onInscribirme;
  final void Function(Participante) onCancelar;

  @override
  Widget build(BuildContext context) {
    if (user == null) return const SizedBox.shrink();
    final estado = salida.estado.toUpperCase();
    if (estado != 'PLANIFICADA' && estado != 'EN_CURSO') {
      return const SizedBox.shrink();
    }

    final mine = salida.participantes
        .where((p) => p.socioId == user!.socioId)
        .firstOrNull;
    final estadoInscripcion = mine?.estadoInscripcion.toUpperCase();

    bool puedeCancelarInscrito() {
      if (mine == null || salida.fechaInicio == null) return true;
      final cutoff =
          salida.fechaInicio!.subtract(const Duration(hours: 48));
      return DateTime.now().isBefore(cutoff);
    }

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Tu inscripción',
              style:
                  AppTextStyles.titleMedium.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 10),

          // ── Sin inscripción ─────────────────────────────────────────────
          if (estadoInscripcion == null || estadoInscripcion == 'CANCELADO') ...[
            Text(
              mine == null
                  ? 'No estás inscrito en esta salida.'
                  : 'Tu inscripción fue cancelada.',
              style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
            ),
            const SizedBox(height: 10),
            if (estado == 'PLANIFICADA' &&
                !salida.inscripcionesCerradas &&
                onInscribirme != null)
              AppButton(
                label:
                    mine == null ? 'Inscribirme' : 'Volver a inscribirme',
                icon: Icons.how_to_reg_outlined,
                fullWidth: true,
                onPressed: onInscribirme,
              )
            else if (salida.inscripcionesCerradas)
              _hint('Las inscripciones están cerradas.')
            else
              _hint('Las inscripciones no están disponibles.'),
          ],

          // ── Pendiente de aprobación ─────────────────────────────────────
          if (estadoInscripcion == 'PENDIENTE_APROBACION') ...[
            Row(children: [
              const Icon(Icons.hourglass_empty,
                  size: 18, color: AppColors.salidaPlanificada),
              const SizedBox(width: 8),
              Text('Pendiente de aprobación',
                  style: AppTextStyles.bodyMedium
                      .copyWith(fontWeight: FontWeight.w600)),
            ]),
            const SizedBox(height: 8),
            // Detalle de aprobadores
            _AprobadorRow(
              label: 'Jefe de Montaña',
              aprobado: mine!.riesgoAprobadoPorDirectivo,
              nombre: mine.riesgoAprobadoPorDirectivoNombre,
            ),
            const SizedBox(height: 4),
            _AprobadorRow(
              label: 'Jefe de Salida',
              aprobado: mine.riesgoAprobadoPorJefe,
              nombre: mine.riesgoAprobadoPorJefeNombre,
            ),
            const SizedBox(height: 10),
            AppButton(
              label: 'Cancelar solicitud',
              variant: AppButtonVariant.destructive,
              icon: Icons.cancel_outlined,
              fullWidth: true,
              onPressed: () => onCancelar(mine),
            ),
          ],

          // ── Inscrito / Confirmado ───────────────────────────────────────
          if (estadoInscripcion == 'INSCRITO' ||
              estadoInscripcion == 'CONFIRMADO') ...[
            Row(children: [
              const Icon(Icons.check_circle_outline,
                  size: 18, color: AppColors.salidaRealizada),
              const SizedBox(width: 8),
              Text('Inscrito',
                  style: AppTextStyles.bodyMedium
                      .copyWith(fontWeight: FontWeight.w600)),
            ]),
            if (mine!.motivoDirectivo != null) ...[
              const SizedBox(height: 6),
              _MotivoRow(
                  quien: 'Jefe de Montaña', motivo: mine.motivoDirectivo!),
            ],
            if (mine.motivoJefe != null) ...[
              const SizedBox(height: 4),
              _MotivoRow(
                  quien: 'Jefe de Salida', motivo: mine.motivoJefe!),
            ],
            const SizedBox(height: 10),
            if (esJefeSalidaPropio && !isPrivileged)
              _hint(
                'Eres Jefe de Salida. Para retirarte, contacta al '
                'Jefe de Montaña.',
              )
            else if (puedeCancelarInscrito())
              AppButton(
                label: 'Cancelar inscripción',
                variant: AppButtonVariant.destructive,
                icon: Icons.cancel_outlined,
                fullWidth: true,
                onPressed: () => onCancelar(mine),
              )
            else
              _hint('No se puede cancelar (menos de 48 h para la salida).'),
          ],

          // ── Negado ─────────────────────────────────────────────────────
          if (estadoInscripcion == 'NEGADO') ...[
            Row(children: [
              const Icon(Icons.cancel_outlined,
                  size: 18, color: AppColors.destructive),
              const SizedBox(width: 8),
              Text('Tu inscripción fue negada.',
                  style: AppTextStyles.bodyMedium.copyWith(
                      fontWeight: FontWeight.w600,
                      color: AppColors.destructive)),
            ]),
            if (mine!.motivoDirectivo != null) ...[
              const SizedBox(height: 6),
              _MotivoRow(
                  quien: 'Jefe de Montaña', motivo: mine.motivoDirectivo!),
            ],
            if (mine.motivoJefe != null) ...[
              const SizedBox(height: 4),
              _MotivoRow(
                  quien: 'Jefe de Salida', motivo: mine.motivoJefe!),
            ],
          ],
        ],
      ),
    );
  }

  static Widget _hint(String text) => Text(
        text,
        style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
      );
}

class _AprobadorRow extends StatelessWidget {
  const _AprobadorRow({
    required this.label,
    required this.aprobado,
    this.nombre,
  });
  final String label;
  final bool aprobado;
  final String? nombre;

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Icon(
        aprobado ? Icons.check_circle_outline : Icons.access_time,
        size: 14,
        color: aprobado ? AppColors.salidaRealizada : AppColors.salidaPlanificada,
      ),
      const SizedBox(width: 6),
      Text(
        aprobado
            ? '$label: ${nombre ?? "aprobado"}'
            : '$label: pendiente',
        style: AppTextStyles.bodySmall.copyWith(
          color: aprobado ? AppColors.salidaRealizada : AppColors.mutedFg,
        ),
      ),
    ]);
  }
}

class _MotivoRow extends StatelessWidget {
  const _MotivoRow({required this.quien, required this.motivo});
  final String quien;
  final String motivo;

  @override
  Widget build(BuildContext context) => Text.rich(
        TextSpan(children: [
          TextSpan(
            text: '$quien: ',
            style: AppTextStyles.bodySmall
                .copyWith(fontWeight: FontWeight.w600),
          ),
          TextSpan(
            text: motivo,
            style: AppTextStyles.bodySmall
                .copyWith(color: AppColors.mutedFg),
          ),
        ]),
      );
}

// ── Utilidades ───────────────────────────────────────────────────────────────

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.icon, required this.label, this.color});
  final IconData icon;
  final String label;
  final Color? color;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(children: [
          Icon(icon, size: 16, color: color ?? AppColors.mutedFg),
          const SizedBox(width: 8),
          Expanded(
              child: Text(label,
                  style: AppTextStyles.bodySmall.copyWith(
                      color: color ?? AppColors.foreground))),
        ]),
      );
}

// ── Fila de participante ─────────────────────────────────────────────────────

class _ParticipanteItem extends StatelessWidget {
  const _ParticipanteItem({
    required this.p,
    this.canEdit = false,
    this.hayJefe = false,
    this.dignidadesDisponibles = const [],
    this.esJefeMontana = false,
    this.esJefeSalidaPropio = false,
    this.onDesignarJefe,
    this.onAgregarDignidad,
    this.onQuitarDignidad,
    this.onDecidirRiesgo,
    this.onRevocarAprobacion,
  });

  final Participante p;
  final bool canEdit;
  final bool hayJefe;
  final List<Dignidad> dignidadesDisponibles;
  final bool esJefeMontana;
  final bool esJefeSalidaPropio;
  final VoidCallback? onDesignarJefe;
  final void Function(int dignidadId, String nombre)? onAgregarDignidad;
  final void Function(int asignadaId, String nombre)? onQuitarDignidad;
  final Future<void> Function(bool aprobar, String motivo)? onDecidirRiesgo;
  final VoidCallback? onRevocarAprobacion;

  bool get _esPendiente =>
      p.estadoInscripcion.toUpperCase() == 'PENDIENTE_APROBACION';

  @override
  Widget build(BuildContext context) {
    final puedeDesignar = onDesignarJefe != null;
    final puedeAgregar = onAgregarDignidad != null;
    final hayDignidades = p.dignidades.isNotEmpty;
    final hayAcciones = puedeDesignar || hayDignidades || puedeAgregar;
    final mostrarAprobacion = p.nivelInsuficiente && _esPendiente;

    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.sidebar,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: p.esJefe
                ? AppColors.salidaPlanificada.withValues(alpha: 0.4)
                : _esPendiente
                    ? AppColors.salidaPlanificada.withValues(alpha: 0.25)
                    : AppColors.border,
          ),
        ),
        child: Column(
          children: [
            ListTile(
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
              leading: AppAvatar(name: p.nombre, size: 36),
              title: Row(children: [
                Expanded(
                    child: Text(p.nombre, style: AppTextStyles.bodyMedium)),
                _estadoBadge(p.estadoInscripcion),
              ]),
              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (p.esJefe)
                    Text('Jefe de Salida',
                        style: AppTextStyles.bodySmall.copyWith(
                            color: AppColors.salidaPlanificada,
                            fontWeight: FontWeight.w600)),
                  if (p.nivelTecnico != null)
                    Text(p.nivelTecnico!,
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg)),
                  if (_esPendiente && p.nivelInsuficiente) ...[
                    Text(
                      'Nivel insuficiente'
                      '${p.nivelMinimoRequeridoNombre != null ? " — mínimo: ${p.nivelMinimoRequeridoNombre}" : ""}',
                      style: AppTextStyles.labelSmall.copyWith(
                          color: AppColors.salidaPlanificada),
                    ),
                  ],
                ],
              ),
            ),

            // ── Panel de aprobación de riesgo ──────────────────────────────
            if (mostrarAprobacion) ...[
              const Divider(height: 1, color: AppColors.border),
              Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Aprobación requerida',
                        style: AppTextStyles.labelSmall
                            .copyWith(color: AppColors.mutedFg)),
                    const SizedBox(height: 8),
                    // Fila Jefe de Montaña
                    _AprobacionRiesgoRow(
                      label: 'Jefe de Montaña',
                      aprobado: p.riesgoAprobadoPorDirectivo,
                      nombre: p.riesgoAprobadoPorDirectivoNombre,
                      puedeActuar: esJefeMontana,
                      onDecidir: onDecidirRiesgo != null
                          ? () =>
                              _showDecisionForm(context, onDecidirRiesgo!)
                          : null,
                      onRevocar: (esJefeMontana && p.riesgoAprobadoPorDirectivo)
                          ? onRevocarAprobacion
                          : null,
                    ),
                    const SizedBox(height: 6),
                    // Fila Jefe de Salida
                    _AprobacionRiesgoRow(
                      label: 'Jefe de Salida',
                      aprobado: p.riesgoAprobadoPorJefe,
                      nombre: p.riesgoAprobadoPorJefeNombre,
                      puedeActuar: esJefeSalidaPropio,
                      onDecidir: onDecidirRiesgo != null
                          ? () =>
                              _showDecisionForm(context, onDecidirRiesgo!)
                          : null,
                      onRevocar:
                          (esJefeSalidaPropio && p.riesgoAprobadoPorJefe)
                              ? onRevocarAprobacion
                              : null,
                    ),
                    // Motivos registrados
                    if (p.motivoDirectivo != null) ...[
                      const SizedBox(height: 8),
                      _MotivoRow(
                          quien: 'JM', motivo: p.motivoDirectivo!),
                    ],
                    if (p.motivoJefe != null) ...[
                      const SizedBox(height: 4),
                      _MotivoRow(
                          quien: 'JS', motivo: p.motivoJefe!),
                    ],
                  ],
                ),
              ),
            ],

            // ── Dignidades ─────────────────────────────────────────────────
            if (hayAcciones) ...[
              const Divider(height: 1, color: AppColors.border),
              Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                child: Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    ...p.dignidades.map((d) => _DignidadChip(
                          nombre: d.nombre,
                          onQuitar: onQuitarDignidad != null
                              ? () =>
                                  onQuitarDignidad!(d.asignadaId, d.nombre)
                              : null,
                        )),
                    if (puedeAgregar)
                      _AgregarDignidadChip(
                        disponibles: dignidadesDisponibles,
                        onPicked: (id, nombre) =>
                            onAgregarDignidad!(id, nombre),
                      ),
                    if (puedeDesignar)
                      _DesignarJefeChip(
                        nombreParticipante: p.nombre,
                        onConfirm: onDesignarJefe!,
                      ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _estadoBadge(String estado) {
    final color = switch (estado.toUpperCase()) {
      'CONFIRMADO' => AppColors.salidaRealizada,
      'CANCELADO' => AppColors.salidaCancelada,
      'NEGADO' => AppColors.salidaCancelada,
      'NO_FUE' => AppColors.mutedFg,
      'PENDIENTE_APROBACION' => AppColors.salidaPlanificada,
      _ => AppColors.salidaEnCurso,
    };
    final label = switch (estado.toUpperCase()) {
      'PENDIENTE_APROBACION' => 'Pendiente',
      'NO_FUE' => 'No fue',
      _ => estado,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(label,
          style: TextStyle(
              color: color, fontSize: 10, fontWeight: FontWeight.w600)),
    );
  }
}

Future<void> _showDecisionForm(
  BuildContext context,
  Future<void> Function(bool aprobar, String motivo) onConfirm,
) async {
  bool aprobar = true;
  String motivo = '';

  final confirmed = await showDialog<bool>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, setLocal) => AlertDialog(
        backgroundColor: AppColors.sidebar,
        title: const Text('Decidir inscripción',
            style: TextStyle(color: AppColors.foreground)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Expanded(
                child: _DecisionToggle(
                  value: true,
                  selected: aprobar,
                  label: 'Aprobar',
                  icon: Icons.check_circle_outline,
                  color: AppColors.salidaRealizada,
                  onTap: () => setLocal(() => aprobar = true),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _DecisionToggle(
                  value: false,
                  selected: !aprobar,
                  label: 'Negar',
                  icon: Icons.cancel_outlined,
                  color: AppColors.destructive,
                  onTap: () => setLocal(() => aprobar = false),
                ),
              ),
            ]),
            const SizedBox(height: 12),
            TextField(
              maxLength: 500,
              minLines: 2,
              maxLines: 4,
              autofocus: true,
              style: const TextStyle(color: AppColors.foreground),
              onChanged: (v) => setLocal(() => motivo = v),
              decoration: const InputDecoration(
                hintText: 'Motivo (obligatorio)',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: motivo.trim().isNotEmpty
                ? () => Navigator.pop(ctx, true)
                : null,
            child: Text(
              'Confirmar',
              style: TextStyle(
                color: aprobar
                    ? AppColors.salidaRealizada
                    : AppColors.destructive,
              ),
            ),
          ),
        ],
      ),
    ),
  );

  if (confirmed == true) {
    await onConfirm(aprobar, motivo.trim());
  }
}

class _DecisionToggle extends StatelessWidget {
  const _DecisionToggle({
    required this.value,
    required this.selected,
    required this.label,
    required this.icon,
    required this.color,
    required this.onTap,
  });
  final bool value;
  final bool selected;
  final String label;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: selected ? color.withValues(alpha: 0.15) : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: selected ? color : AppColors.border,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 16, color: selected ? color : AppColors.mutedFg),
            const SizedBox(width: 6),
            Text(
              label,
              style: AppTextStyles.bodySmall.copyWith(
                color: selected ? color : AppColors.mutedFg,
                fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Fila de aprobación por aprobador ─────────────────────────────────────────

class _AprobacionRiesgoRow extends StatelessWidget {
  const _AprobacionRiesgoRow({
    required this.label,
    required this.aprobado,
    this.nombre,
    required this.puedeActuar,
    this.onDecidir,
    this.onRevocar,
  });
  final String label;
  final bool aprobado;
  final String? nombre;
  final bool puedeActuar;
  final VoidCallback? onDecidir;
  final VoidCallback? onRevocar;

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Icon(
        aprobado ? Icons.check_circle_outline : Icons.access_time,
        size: 14,
        color: aprobado ? AppColors.salidaRealizada : AppColors.salidaPlanificada,
      ),
      const SizedBox(width: 6),
      Expanded(
        child: Text(
          aprobado
              ? '$label: ${nombre ?? "aprobado"}'
              : '$label: pendiente',
          style: AppTextStyles.bodySmall.copyWith(
              color: aprobado ? AppColors.salidaRealizada : AppColors.mutedFg),
        ),
      ),
      if (puedeActuar && !aprobado && onDecidir != null)
        GestureDetector(
          onTap: onDecidir,
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(4),
              border: Border.all(
                  color: AppColors.salidaPlanificada.withValues(alpha: 0.6)),
            ),
            child: Text('Decidir',
                style: AppTextStyles.labelSmall
                    .copyWith(color: AppColors.salidaPlanificada)),
          ),
        ),
      if (puedeActuar && aprobado && onRevocar != null)
        GestureDetector(
          onTap: onRevocar,
          child: Text('Cambiar',
              style: AppTextStyles.labelSmall
                  .copyWith(color: AppColors.mutedFg)),
        ),
    ]);
  }
}

// ── Dignidades widgets ────────────────────────────────────────────────────────

class _DignidadChip extends StatelessWidget {
  const _DignidadChip({required this.nombre, this.onQuitar});
  final String nombre;
  final VoidCallback? onQuitar;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.only(left: 8, top: 3, bottom: 3, right: 4),
      decoration: BoxDecoration(
        color: AppColors.secondary,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(nombre,
              style: AppTextStyles.bodySmall
                  .copyWith(color: AppColors.foreground)),
          if (onQuitar != null) ...[
            const SizedBox(width: 2),
            GestureDetector(
              onTap: onQuitar,
              child:
                  const Icon(Icons.close, size: 14, color: AppColors.mutedFg),
            ),
          ],
        ],
      ),
    );
  }
}

class _AgregarDignidadChip extends StatelessWidget {
  const _AgregarDignidadChip(
      {required this.disponibles, required this.onPicked});
  final List<Dignidad> disponibles;
  final void Function(int id, String nombre) onPicked;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => _pick(context),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: AppColors.primary.withValues(alpha: 0.5),
              style: BorderStyle.solid),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.add,
                size: 14, color: AppColors.primary.withValues(alpha: 0.8)),
            const SizedBox(width: 4),
            Text('Agregar cargo',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.primary)),
          ],
        ),
      ),
    );
  }

  Future<void> _pick(BuildContext context) async {
    final picked = await showModalBottomSheet<Dignidad>(
      context: context,
      backgroundColor: AppColors.sidebar,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (_) => _DignidadPickerSheet(disponibles: disponibles),
    );
    if (picked != null) onPicked(picked.id, picked.nombre);
  }
}

class _DesignarJefeChip extends StatelessWidget {
  const _DesignarJefeChip(
      {required this.nombreParticipante, required this.onConfirm});
  final String nombreParticipante;
  final VoidCallback onConfirm;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => _confirm(context),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: AppColors.salidaPlanificada.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: AppColors.salidaPlanificada.withValues(alpha: 0.5)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.star_outline,
                size: 14, color: AppColors.salidaPlanificada),
            const SizedBox(width: 4),
            Text('Designar jefe',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.salidaPlanificada)),
          ],
        ),
      ),
    );
  }

  Future<void> _confirm(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dc) => AlertDialog(
        backgroundColor: AppColors.sidebar,
        title: const Text('Designar Jefe de Salida',
            style: TextStyle(color: AppColors.foreground)),
        content: Text('¿Designar a $nombreParticipante como Jefe de Salida?',
            style: const TextStyle(color: AppColors.mutedFg)),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dc, false),
              child: const Text('Cancelar')),
          TextButton(
              onPressed: () => Navigator.pop(dc, true),
              child: const Text('Designar')),
        ],
      ),
    );
    if (ok == true) onConfirm();
  }
}

class _DignidadPickerSheet extends StatelessWidget {
  const _DignidadPickerSheet({required this.disponibles});
  final List<Dignidad> disponibles;

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).padding.bottom;
    return SafeArea(
      top: false,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 12),
          Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                  color: AppColors.border,
                  borderRadius: BorderRadius.circular(2))),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text('Asignar cargo',
                style: AppTextStyles.titleMedium
                    .copyWith(fontWeight: FontWeight.w600)),
          ),
          const SizedBox(height: 8),
          ...disponibles.map((d) => ListTile(
                title: Text(d.nombre, style: AppTextStyles.bodyMedium),
                onTap: () => Navigator.pop(context, d),
              )),
          SizedBox(height: bottomInset > 0 ? bottomInset : 16),
        ],
      ),
    );
  }
}
