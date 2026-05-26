import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import '../../../../core/api/app_exception.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../domain/models/acta_model.dart';
import '../providers/actas_provider.dart';
import 'acta_crear_screen.dart';

class ActasScreen extends ConsumerStatefulWidget {
  const ActasScreen({super.key});

  @override
  ConsumerState<ActasScreen> createState() => _ActasScreenState();
}

class _ActasScreenState extends ConsumerState<ActasScreen>
    with TickerProviderStateMixin {
  TabController? _tabController;
  bool _isDirectivo = false;
  bool _isAdminOrSec = false;
  bool _isSecretaria = false;
  int _sociosOuterKey = 0;
  int _directivaOuterKey = 0;

  @override
  void initState() {
    super.initState();
    final auth = ref.read(authNotifierProvider).asData?.value;
    if (auth is AuthAuthenticated) {
      final role = auth.user.rol;
      _isSecretaria = role == UserRole.secretaria;
      _isAdminOrSec = role == UserRole.admin || role == UserRole.secretaria;
      _isDirectivo = _isAdminOrSec || role == UserRole.directivo;
      if (_isDirectivo) {
        _tabController = TabController(length: 2, vsync: this)
          ..addListener(() => setState(() {}));
      }
    }
  }

  @override
  void dispose() {
    _tabController?.dispose();
    super.dispose();
  }

  String get _currentTipo {
    if (_tabController == null) return 'SOCIOS';
    return _tabController!.index == 0 ? 'SOCIOS' : 'DIRECTIVA';
  }

  void _openCreate(String tipo) {
    Navigator.of(context, rootNavigator: true).push<void>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => ActaCrearScreen(
          tipo: tipo,
          onSaved: () => setState(() {
            if (tipo == 'SOCIOS') {
              _sociosOuterKey++;
            } else {
              _directivaOuterKey++;
            }
          }),
        ),
      ),
    );
  }

  Future<void> _importarActa(String tipo) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['md'],
    );
    if (result == null || result.files.single.path == null) return;
    final filePath = result.files.single.path!;
    if (!mounted) return;

    await showDialog<void>(
      context: context,
      builder: (_) => _ImportPreviewDialog(
        filePath: filePath,
        onImported: () => setState(() {
          if (tipo == 'SOCIOS') {
            _sociosOuterKey++;
          } else {
            _directivaOuterKey++;
          }
        }),
      ),
    );
  }

  void _showActionSheet() {
    final tipo = _currentTipo;
    final canImport = tipo == 'SOCIOS' ? _isAdminOrSec : _isSecretaria;

    showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 8),
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.border,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 4),
            ListTile(
              leading:
                  const Icon(Icons.add_circle_outline, color: AppColors.primary),
              title: const Text('Nueva acta'),
              onTap: () {
                Navigator.pop(context);
                _openCreate(tipo);
              },
            ),
            if (canImport)
              ListTile(
                leading: const Icon(Icons.upload_file_outlined,
                    color: AppColors.primary),
                title: const Text('Importar .md'),
                onTap: () {
                  Navigator.pop(context);
                  _importarActa(tipo);
                },
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Actas de Reunión'),
        centerTitle: false,
        bottom: _isDirectivo && _tabController != null
            ? TabBar(
                controller: _tabController,
                labelColor: AppColors.primary,
                unselectedLabelColor: AppColors.mutedFg,
                indicatorColor: AppColors.primary,
                indicatorSize: TabBarIndicatorSize.label,
                tabs: const [
                  Tab(text: 'Socios'),
                  Tab(text: 'Directiva'),
                ],
              )
            : null,
      ),
      floatingActionButton: _isAdminOrSec
          ? FloatingActionButton(
              onPressed: _showActionSheet,
              child: const Icon(Icons.add),
            )
          : null,
      body: _isDirectivo && _tabController != null
          ? TabBarView(
              controller: _tabController,
              children: [
                _ActasList(
                  key: ValueKey('socios-$_sociosOuterKey'),
                  tipo: 'SOCIOS',
                  canManage: _isAdminOrSec,
                ),
                _ActasList(
                  key: ValueKey('directiva-$_directivaOuterKey'),
                  tipo: 'DIRECTIVA',
                  canManage: _isAdminOrSec,
                ),
              ],
            )
          : _ActasList(
              key: ValueKey('socios-$_sociosOuterKey'),
              tipo: 'SOCIOS',
              canManage: false,
            ),
    );
  }
}

// ── Per-tab list ────────────────────────────────────────────────────────────

class _ActasList extends ConsumerStatefulWidget {
  const _ActasList({
    required this.tipo,
    required this.canManage,
    super.key,
  });
  final String tipo;
  final bool canManage;

  @override
  ConsumerState<_ActasList> createState() => _ActasListState();
}

class _ActasListState extends ConsumerState<_ActasList> {
  int _innerKey = 0;

  void _refresh() => setState(() => _innerKey++);

  Future<void> _confirmDelete(Acta acta) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        backgroundColor: AppColors.background,
        title: const Text('Eliminar acta'),
        content: Text(
          '¿Eliminar ${acta.numero != null ? "Acta N° ${acta.numero}" : "esta acta"}? '
          'Esta acción no se puede deshacer.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c, false),
            child: const Text('Cancelar'),
          ),
          AppButton(
            label: 'Eliminar',
            variant: AppButtonVariant.destructive,
            onPressed: () => Navigator.pop(c, true),
          ),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await ref.read(actasRepositoryProvider).deleteActa(acta.id);
      _refresh();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al eliminar: $e')),
        );
      }
    }
  }

  void _openEdit(Acta acta) {
    Navigator.of(context, rootNavigator: true).push<void>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => ActaCrearScreen(
          tipo: widget.tipo,
          existingId: acta.id,
          onSaved: _refresh,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AppPagedList<Acta>(
      key: ValueKey('${widget.tipo}-$_innerKey'),
      loader: (page) => ref
          .read(actasRepositoryProvider)
          .getActas(page: page, tipo: widget.tipo),
      emptyMessage:
          'Sin actas de ${widget.tipo == "SOCIOS" ? "socios" : "directiva"}',
      itemBuilder: (_, acta, i) => _ActaItem(
        acta: acta,
        canManage: widget.canManage,
        onEdit: () => _openEdit(acta),
        onDelete: () => _confirmDelete(acta),
      ),
    );
  }
}

// ── List item ────────────────────────────────────────────────────────────────

class _ActaItem extends StatelessWidget {
  const _ActaItem({
    required this.acta,
    required this.canManage,
    required this.onEdit,
    required this.onDelete,
  });
  final Acta acta;
  final bool canManage;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd/MM/yyyy', 'es');
    return AppCard(
      onTap: () => context.push('/actas/${acta.id}'),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child:
                const Icon(Icons.article_outlined, color: AppColors.primary),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  acta.numero != null
                      ? 'Acta N° ${acta.numero}'
                      : 'Acta sin número',
                  style: AppTextStyles.bodyLarge
                      .copyWith(fontWeight: FontWeight.w600),
                ),
                if (acta.fecha != null)
                  Text(df.format(acta.fecha!),
                      style: AppTextStyles.bodySmall
                          .copyWith(color: AppColors.mutedFg)),
                if (acta.tipo != null)
                  Text(acta.tipo!,
                      style: AppTextStyles.labelSmall
                          .copyWith(color: AppColors.mutedFg)),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              if (acta.tienePdf)
                const Icon(Icons.picture_as_pdf,
                    color: AppColors.salidaRealizada, size: 18),
              Text('${acta.totalAsistentes}',
                  style: AppTextStyles.titleMedium.copyWith(
                      color: AppColors.primary, fontWeight: FontWeight.bold)),
              Text('asistentes',
                  style: AppTextStyles.labelSmall
                      .copyWith(color: AppColors.mutedFg)),
            ],
          ),
          if (canManage) ...[
            const SizedBox(width: 4),
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert,
                  color: AppColors.mutedFg, size: 20),
              onSelected: (v) {
                if (v == 'edit') onEdit();
                if (v == 'delete') onDelete();
              },
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'edit', child: Text('Editar')),
                PopupMenuItem(value: 'delete', child: Text('Eliminar')),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

// ── Import preview dialog ────────────────────────────────────────────────────

class _ImportPreviewDialog extends ConsumerStatefulWidget {
  const _ImportPreviewDialog({
    required this.filePath,
    required this.onImported,
  });

  final String filePath;
  final VoidCallback onImported;

  @override
  ConsumerState<_ImportPreviewDialog> createState() =>
      _ImportPreviewDialogState();
}

class _ImportPreviewDialogState extends ConsumerState<_ImportPreviewDialog> {
  bool _loading = true;
  String? _error;
  Map<String, dynamic>? _preview;

  @override
  void initState() {
    super.initState();
    _loadPreview();
  }

  Future<void> _loadPreview() async {
    try {
      final data = await ref
          .read(actasRepositoryProvider)
          .importarPreview(widget.filePath);
      setState(() {
        _preview = data;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = unwrapDio(e).toString();
        _loading = false;
      });
    }
  }

  Future<void> _confirm() async {
    setState(() => _loading = true);
    try {
      await ref
          .read(actasRepositoryProvider)
          .importarConfirmar(_preview!);
      widget.onImported();
      if (mounted) Navigator.pop(context);
    } catch (e) {
      setState(() {
        _error = unwrapDio(e).toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.background,
      title: const Text('Importar acta'),
      content: SizedBox(
        width: double.maxFinite,
        child: _loading
            ? const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: CircularProgressIndicator(),
                ),
              )
            : _error != null
                ? Text('Error: $_error',
                    style: const TextStyle(color: AppColors.destructive))
                : _PreviewContent(preview: _preview!),
      ),
      actions: _loading
          ? null
          : [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Cancelar'),
              ),
              if (_error == null)
                AppButton(
                  label: 'Confirmar importación',
                  onPressed: _confirm,
                ),
            ],
    );
  }
}

class _PreviewContent extends StatelessWidget {
  const _PreviewContent({required this.preview});
  final Map<String, dynamic> preview;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (preview['titulo'] != null)
            Text(preview['titulo'] as String,
                style: AppTextStyles.titleSmall),
          if (preview['fecha'] != null)
            Text('Fecha: ${preview['fecha']}',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
          if (preview['tipo'] != null)
            Text('Tipo: ${preview['tipo']}',
                style: AppTextStyles.bodySmall
                    .copyWith(color: AppColors.mutedFg)),
          const SizedBox(height: 8),
          const Text('Vista previa del contenido:',
              style: TextStyle(color: AppColors.mutedFg, fontSize: 12)),
          const SizedBox(height: 4),
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.secondary,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              preview['contenidoPreview'] as String? ?? '(sin vista previa)',
              style: const TextStyle(
                  color: AppColors.foreground,
                  fontSize: 12,
                  fontFamily: 'monospace'),
              maxLines: 15,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
