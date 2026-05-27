import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../../core/widgets/app_input.dart';
import '../../domain/models/acta_model.dart';
import '../providers/actas_provider.dart';

class ActaCrearScreen extends ConsumerStatefulWidget {
  const ActaCrearScreen({
    required this.tipo,
    this.existingId,
    required this.onSaved,
    super.key,
  });

  final String tipo;
  final String? existingId;
  final VoidCallback onSaved;

  @override
  ConsumerState<ActaCrearScreen> createState() => _ActaCrearScreenState();
}

class _ActaCrearScreenState extends ConsumerState<ActaCrearScreen> {
  final _numeroCtrl = TextEditingController();
  final _lugarCtrl = TextEditingController();
  final _descripcionCtrl = TextEditingController();
  final _actividadesRealizadasCtrl = TextEditingController();
  final _actividadesPorRealizarCtrl = TextEditingController();
  final _acuerdosCtrl = TextEditingController();
  final _variosCtrl = TextEditingController();
  final _observacionesCtrl = TextEditingController();

  DateTime? _fecha;
  bool _loading = false;
  bool _prefilling = false;

  bool get _isEdit => widget.existingId != null;

  @override
  void initState() {
    super.initState();
    if (_isEdit) _prefill();
  }

  @override
  void dispose() {
    _numeroCtrl.dispose();
    _lugarCtrl.dispose();
    _descripcionCtrl.dispose();
    _actividadesRealizadasCtrl.dispose();
    _actividadesPorRealizarCtrl.dispose();
    _acuerdosCtrl.dispose();
    _variosCtrl.dispose();
    _observacionesCtrl.dispose();
    super.dispose();
  }

  Future<void> _prefill() async {
    setState(() => _prefilling = true);
    try {
      final d = await ref.read(actasRepositoryProvider).getActaDetail(widget.existingId!);
      if (!mounted) return;
      setState(() {
        _numeroCtrl.text = d.numero ?? '';
        _lugarCtrl.text = d.lugar ?? '';
        _descripcionCtrl.text = d.descripcion ?? '';
        _actividadesRealizadasCtrl.text = d.actividadesRealizadas ?? '';
        _actividadesPorRealizarCtrl.text = d.actividadesPorRealizar ?? '';
        _acuerdosCtrl.text = d.acuerdos ?? '';
        _variosCtrl.text = d.varios ?? '';
        _observacionesCtrl.text = d.observaciones ?? '';
        _fecha = d.fecha;
        _prefilling = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _prefilling = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error al cargar acta: $e')),
      );
    }
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _fecha ?? DateTime.now(),
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      locale: const Locale('es'),
    );
    if (picked != null) setState(() => _fecha = picked);
  }

  Future<void> _submit() async {
    setState(() => _loading = true);
    try {
      final req = CreateActaRequest(
        tipo: widget.tipo,
        numero: _numeroCtrl.text.trim().isEmpty ? null : _numeroCtrl.text.trim(),
        fecha: _fecha,
        lugar: _lugarCtrl.text.trim().isEmpty ? null : _lugarCtrl.text.trim(),
        descripcion: _descripcionCtrl.text.trim().isEmpty ? null : _descripcionCtrl.text.trim(),
        actividadesRealizadas: _actividadesRealizadasCtrl.text.trim().isEmpty
            ? null
            : _actividadesRealizadasCtrl.text.trim(),
        actividadesPorRealizar: _actividadesPorRealizarCtrl.text.trim().isEmpty
            ? null
            : _actividadesPorRealizarCtrl.text.trim(),
        acuerdos: _acuerdosCtrl.text.trim().isEmpty ? null : _acuerdosCtrl.text.trim(),
        varios: _variosCtrl.text.trim().isEmpty ? null : _variosCtrl.text.trim(),
        observaciones: _observacionesCtrl.text.trim().isEmpty ? null : _observacionesCtrl.text.trim(),
      );
      if (_isEdit) {
        await ref.read(actasRepositoryProvider).updateActa(widget.existingId!, req);
      } else {
        await ref.read(actasRepositoryProvider).createActa(req);
      }
      widget.onSaved();
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        setState(() => _loading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final df = DateFormat('dd/MM/yyyy', 'es');
    final tipoLabel = widget.tipo == 'DIRECTIVA' ? 'Directiva' : 'Socios';

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text(_isEdit ? 'Editar acta' : 'Nueva acta'),
      ),
      body: _prefilling
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                AppCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Acta de $tipoLabel',
                        style: AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _numeroCtrl,
                        label: 'Número de reunión',
                        keyboardType: TextInputType.number,
                      ),
                      const SizedBox(height: 12),
                      InkWell(
                        onTap: _pickDate,
                        child: InputDecorator(
                          decoration: const InputDecoration(
                            labelText: 'Fecha',
                            border: OutlineInputBorder(),
                          ),
                          child: Text(
                            _fecha != null ? df.format(_fecha!) : 'Seleccionar fecha',
                            style: AppTextStyles.bodyMedium.copyWith(
                              color: _fecha != null ? null : AppColors.mutedFg,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _lugarCtrl,
                        label: 'Lugar',
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _descripcionCtrl,
                        label: 'Descripción',
                        maxLines: 3,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                AppCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Contenido',
                        style: AppTextStyles.titleSmall.copyWith(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _actividadesRealizadasCtrl,
                        label: 'Actividades realizadas',
                        maxLines: 4,
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _actividadesPorRealizarCtrl,
                        label: 'Actividades por realizar',
                        maxLines: 4,
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _acuerdosCtrl,
                        label: 'Acuerdos',
                        maxLines: 4,
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _variosCtrl,
                        label: 'Varios',
                        maxLines: 3,
                      ),
                      const SizedBox(height: 12),
                      AppInput(
                        controller: _observacionesCtrl,
                        label: 'Observaciones',
                        maxLines: 3,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),

                AppButton(
                  label: _isEdit ? 'Guardar cambios' : 'Crear acta',
                  fullWidth: true,
                  loading: _loading,
                  onPressed: _loading ? null : _submit,
                ),
                const SizedBox(height: 24),
              ],
            ),
    );
  }
}
