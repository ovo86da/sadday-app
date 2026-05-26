import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/api/app_exception.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/auth/auth_state.dart';
import '../../../../core/auth/user_model.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../domain/models/montana_model.dart';
import '../providers/montanas_provider.dart';

class MontanasScreen extends ConsumerStatefulWidget {
  const MontanasScreen({super.key});

  @override
  ConsumerState<MontanasScreen> createState() => _MontanasScreenState();
}

class _MontanasScreenState extends ConsumerState<MontanasScreen> {
  final _searchController = TextEditingController();
  String _q = '';
  int _listKey = 0;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _search(String q) => setState(() {
        _q = q;
        _listKey++;
      });

  bool get _canCreate {
    final auth = ref.read(authNotifierProvider).asData?.value;
    if (auth is! AuthAuthenticated) return false;
    final rol = auth.user.rol;
    return rol == UserRole.admin ||
        rol == UserRole.secretaria ||
        rol == UserRole.directivo;
  }

  void _showCrear(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _MontanaFormSheet(
        onSaved: () => setState(() => _listKey++),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      floatingActionButton: _canCreate
          ? FloatingActionButton(
              onPressed: () => _showCrear(context),
              tooltip: 'Nueva montaña',
              child: const Icon(Icons.add),
            )
          : null,
      appBar: AppBar(
        title: const Text('Montañas'),
        centerTitle: false,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Buscar montaña...',
                prefixIcon: const Icon(Icons.search, size: 20),
                isDense: true,
                suffixIcon: _q.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          _searchController.clear();
                          _search('');
                        })
                    : null,
              ),
              onChanged: _search,
            ),
          ),
        ),
      ),
      body: AppPagedList<Montana>(
        key: ValueKey(_listKey),
        loader: (page) => ref
            .read(montanasRepositoryProvider)
            .getMontanas(page: page, q: _q.isEmpty ? null : _q),
        emptyMessage: 'Sin montañas',
        itemBuilder: (_, m, _) => _MontanaItem(montana: m),
      ),
    );
  }
}

class _MontanaItem extends StatelessWidget {
  const _MontanaItem({required this.montana});
  final Montana montana;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      onTap: () => context.push('/montanas/${montana.id}'),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.landscape, color: AppColors.primary),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(montana.nombre,
                    style: AppTextStyles.bodyLarge
                        .copyWith(fontWeight: FontWeight.w600)),
                Row(children: [
                  if (montana.pais != null) ...[
                    const Icon(Icons.flag_outlined,
                        size: 13, color: AppColors.mutedFg),
                    const SizedBox(width: 4),
                    Text(montana.pais!,
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg)),
                    const SizedBox(width: 12),
                  ],
                  if (montana.altitud != null) ...[
                    const Icon(Icons.height, size: 13, color: AppColors.mutedFg),
                    const SizedBox(width: 4),
                    Text('${montana.altitud!.round()} m',
                        style: AppTextStyles.bodySmall
                            .copyWith(color: AppColors.mutedFg)),
                  ],
                ]),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text('${montana.numRutas}',
                  style: AppTextStyles.titleLarge
                      .copyWith(color: AppColors.primary, fontWeight: FontWeight.bold)),
              Text('rutas',
                  style: AppTextStyles.labelSmall.copyWith(color: AppColors.mutedFg)),
            ],
          ),
        ],
      ),
    );
  }
}

class _MontanaFormSheet extends ConsumerStatefulWidget {
  const _MontanaFormSheet({required this.onSaved});
  final VoidCallback onSaved;

  @override
  ConsumerState<_MontanaFormSheet> createState() => _MontanaFormSheetState();
}

class _MontanaFormSheetState extends ConsumerState<_MontanaFormSheet> {
  final _formKey = GlobalKey<FormState>();
  final _nombreCtrl = TextEditingController();
  final _regionCtrl = TextEditingController();
  final _altitudCtrl = TextEditingController();
  final _paisCtrl = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _nombreCtrl.dispose();
    _regionCtrl.dispose();
    _altitudCtrl.dispose();
    _paisCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() { _loading = true; _error = null; });
    try {
      await ref.read(montanasRepositoryProvider).crearMontana({
        'nombre': _nombreCtrl.text.trim(),
        'region': _regionCtrl.text.trim(),
        'altitud': int.parse(_altitudCtrl.text.trim()),
        'pais': _paisCtrl.text.trim(),
      });
      if (mounted) {
        Navigator.of(context).pop();
        widget.onSaved();
      }
    } catch (e) {
      setState(() { _error = unwrapDio(e).toString(); });
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 28),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Nueva montaña', style: AppTextStyles.titleMedium),
              const SizedBox(height: 20),
              TextFormField(
                controller: _nombreCtrl,
                decoration: const InputDecoration(labelText: 'Nombre *'),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Requerido' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _regionCtrl,
                decoration: const InputDecoration(labelText: 'Región *'),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Requerido' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _altitudCtrl,
                decoration: const InputDecoration(labelText: 'Altitud (m) *'),
                keyboardType: TextInputType.number,
                validator: (v) {
                  if (v == null || v.trim().isEmpty) return 'Requerido';
                  if (int.tryParse(v.trim()) == null) return 'Ingresa un número entero';
                  return null;
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _paisCtrl,
                decoration: const InputDecoration(labelText: 'País *'),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Requerido' : null,
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: AppTextStyles.bodySmall.copyWith(color: AppColors.destructive)),
              ],
              const SizedBox(height: 20),
              AppButton(
                label: 'Crear montaña',
                loading: _loading,
                onPressed: _submit,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
