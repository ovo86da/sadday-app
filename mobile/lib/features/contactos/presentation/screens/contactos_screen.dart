import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_dialog.dart';
import '../../../../core/widgets/app_input.dart';
import '../../../../core/widgets/app_paged_list.dart';
import '../../domain/models/contacto_model.dart';
import '../providers/contactos_provider.dart';

class ContactosScreen extends ConsumerStatefulWidget {
  const ContactosScreen({super.key});

  @override
  ConsumerState<ContactosScreen> createState() => _ContactosScreenState();
}

class _ContactosScreenState extends ConsumerState<ContactosScreen> {
  final _search = TextEditingController();
  int _listKey = 0;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Contactos'),
        centerTitle: false,
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showForm(context),
        child: const Icon(Icons.add),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: AppInput(
              controller: _search,
              hint: 'Buscar por nombre o teléfono…',
              prefixIcon: Icons.search,
              onSubmitted: (_) => setState(() => _listKey++),
            ),
          ),
          Expanded(
            child: AppPagedList<Contacto>(
              key: ValueKey('contactos-$_listKey'),
              loader: (p) => ref.read(contactosRepositoryProvider).getContactos(
                    page: p,
                    q: _search.text.trim().isEmpty
                        ? null
                        : _search.text.trim(),
                  ),
              emptyMessage: 'Sin contactos',
              itemBuilder: (ctx, contacto, _) =>
                  _ContactoItem(contacto: contacto, onRefresh: _refresh),
            ),
          ),
        ],
      ),
    );
  }

  void _refresh() => setState(() => _listKey++);

  void _showForm(BuildContext context, [Contacto? contacto]) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _ContactoFormSheet(
        contacto: contacto,
        onSaved: _refresh,
      ),
    );
  }
}

class _ContactoItem extends ConsumerWidget {
  const _ContactoItem({required this.contacto, required this.onRefresh});
  final Contacto contacto;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return AppCard(
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            alignment: Alignment.center,
            child: Icon(_tipoIcon(contacto.tipo),
                color: AppColors.primary, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(contacto.nombre,
                    style: AppTextStyles.bodyMedium
                        .copyWith(fontWeight: FontWeight.w600)),
                if (contacto.telefono != null)
                  Text(contacto.telefono!,
                      style: AppTextStyles.bodySmall
                          .copyWith(color: AppColors.mutedFg)),
                Text(contacto.tipo,
                    style: const TextStyle(
                        color: AppColors.mutedFg, fontSize: 11)),
              ],
            ),
          ),
          PopupMenuButton<String>(
            color: AppColors.background,
            icon: const Icon(Icons.more_vert, color: AppColors.mutedFg),
            itemBuilder: (_) => [
              const PopupMenuItem(value: 'edit', child: Text('Editar')),
              const PopupMenuItem(
                  value: 'delete',
                  child: Text('Eliminar',
                      style: TextStyle(color: AppColors.destructive))),
            ],
            onSelected: (v) {
              if (v == 'edit') {
                _edit(context);
              } else {
                _delete(context, ref);
              }
            },
          ),
        ],
      ),
    );
  }

  IconData _tipoIcon(String tipo) => switch (tipo) {
        'GUIA' => Icons.person_pin_outlined,
        'TRANSPORTE' => Icons.directions_bus_outlined,
        'REFUGIO' => Icons.cabin_outlined,
        _ => Icons.contact_phone_outlined,
      };

  void _edit(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppColors.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => _ContactoFormSheet(
        contacto: contacto,
        onSaved: onRefresh,
      ),
    );
  }

  void _delete(BuildContext context, WidgetRef ref) {
    showAppDialog(
      context: context,
      title: 'Eliminar contacto',
      message: '¿Eliminar a ${contacto.nombre}?',
      confirmLabel: 'Eliminar',
      confirmVariant: AppButtonVariant.destructive,
    ).then((ok) async {
      if (ok == true) {
        await ref.read(contactosRepositoryProvider).eliminar(contacto.id);
        onRefresh();
      }
    });
  }
}

class _ContactoFormSheet extends ConsumerStatefulWidget {
  const _ContactoFormSheet({required this.onSaved, this.contacto});
  final Contacto? contacto;
  final VoidCallback onSaved;

  @override
  ConsumerState<_ContactoFormSheet> createState() =>
      _ContactoFormSheetState();
}

class _ContactoFormSheetState extends ConsumerState<_ContactoFormSheet> {
  final _nombre = TextEditingController();
  final _telefono = TextEditingController();
  final _email = TextEditingController();
  final _notas = TextEditingController();
  String _tipo = 'OTRO';
  bool _loading = false;
  String? _error;

  static const _tipos = ['GUIA', 'TRANSPORTE', 'REFUGIO', 'OTRO'];

  @override
  void initState() {
    super.initState();
    final c = widget.contacto;
    if (c != null) {
      _nombre.text = c.nombre;
      _telefono.text = c.telefono ?? '';
      _email.text = c.email ?? '';
      _notas.text = c.notas ?? '';
      _tipo = c.tipo;
    }
  }

  @override
  void dispose() {
    _nombre.dispose();
    _telefono.dispose();
    _email.dispose();
    _notas.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final data = {
      'nombre': _nombre.text.trim(),
      'telefono': _telefono.text.trim(),
      'email': _email.text.trim(),
      'tipo': _tipo,
      'notas': _notas.text.trim(),
    };
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = ref.read(contactosRepositoryProvider);
      if (widget.contacto == null) {
        await repo.crear(data);
      } else {
        await repo.editar(widget.contacto!.id, data);
      }
      widget.onSaved();
      if (mounted) Navigator.pop(context);
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Center(
              child: Container(
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                      color: AppColors.border,
                      borderRadius: BorderRadius.circular(2))),
            ),
            const SizedBox(height: 16),
            Text(
              widget.contacto == null ? 'Nuevo contacto' : 'Editar contacto',
              style: AppTextStyles.titleMedium,
            ),
            const SizedBox(height: 20),
            AppInput(
                controller: _nombre, hint: 'Nombre', label: 'Nombre *'),
            const SizedBox(height: 12),
            AppInput(
                controller: _telefono,
                hint: 'Teléfono',
                label: 'Teléfono',
                keyboardType: TextInputType.phone),
            const SizedBox(height: 12),
            AppInput(
                controller: _email,
                hint: 'Email',
                label: 'Email',
                keyboardType: TextInputType.emailAddress),
            const SizedBox(height: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Tipo',
                    style: AppTextStyles.bodySmall
                        .copyWith(color: AppColors.mutedFg)),
                const SizedBox(height: 4),
                DropdownButtonFormField<String>(
                  initialValue: _tipo,
                  dropdownColor: AppColors.background,
                  decoration: InputDecoration(
                    filled: true,
                    fillColor: AppColors.secondary,
                    contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 12),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                  ),
                  style: const TextStyle(
                      color: AppColors.foreground, fontSize: 14),
                  items: _tipos
                      .map((t) => DropdownMenuItem(value: t, child: Text(t)))
                      .toList(),
                  onChanged: (v) => setState(() => _tipo = v!),
                ),
              ],
            ),
            const SizedBox(height: 12),
            AppInput(
                controller: _notas,
                hint: 'Notas adicionales',
                label: 'Notas',
                maxLines: 3),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!,
                  style: const TextStyle(
                      color: AppColors.destructive, fontSize: 13)),
            ],
            const SizedBox(height: 24),
            AppButton(
              label: widget.contacto == null ? 'Crear contacto' : 'Guardar',
              loading: _loading,
              onPressed: _save,
            ),
          ],
        ),
      ),
    );
  }
}
