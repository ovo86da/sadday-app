import 'package:flutter/material.dart';
import '../../../../core/api/app_exception.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_card.dart';
import '../../../salidas/presentation/providers/salidas_provider.dart';
import '../../domain/models/informe_model.dart';
import '../providers/informes_provider.dart';

// ─── Segmento state helper ────────────────────────────────────────────────────

class _SegmentoState {
  _SegmentoState()
      : origenCtrl = TextEditingController(text: 'Club Sadday'),
        destinoCtrl = TextEditingController(),
        costoCtrl = TextEditingController();

  _SegmentoState.fromTramo(TramoTransporte t)
      : origenCtrl = TextEditingController(text: t.origen ?? ''),
        destinoCtrl = TextEditingController(text: t.destino ?? ''),
        costoCtrl = TextEditingController(
            text: t.costoIndividual != null
                ? t.costoIndividual!.toString()
                : '') {
    alquiloTransporte = t.alquiloTransporte;
    tipoTransporte = t.tipoTransporte;
  }

  final TextEditingController origenCtrl;
  final TextEditingController destinoCtrl;
  bool alquiloTransporte = false;
  String? tipoTransporte;
  final TextEditingController costoCtrl;

  void dispose() {
    origenCtrl.dispose();
    destinoCtrl.dispose();
    costoCtrl.dispose();
  }

  SegmentoViajeRequest toRequest() => SegmentoViajeRequest(
        origen: origenCtrl.text.trim(),
        destino: destinoCtrl.text.trim(),
        alquiloTransporte: alquiloTransporte,
        tipoTransporte: alquiloTransporte ? tipoTransporte : null,
        costoIndividual:
            alquiloTransporte && costoCtrl.text.trim().isNotEmpty
                ? double.tryParse(costoCtrl.text.trim())
                : null,
      );
}

// ─── Screen ──────────────────────────────────────────────────────────────────

class InformeCrearScreen extends ConsumerStatefulWidget {
  const InformeCrearScreen({
    required this.salidaId,
    this.salidaNombre,
    required this.onSaved,
    this.existingInforme,
    super.key,
  });

  final String salidaId;
  final String? salidaNombre;
  final VoidCallback onSaved;
  final Informe? existingInforme;

  @override
  ConsumerState<InformeCrearScreen> createState() => _InformeCrearScreenState();
}

class _InformeCrearScreenState extends ConsumerState<InformeCrearScreen> {
  bool _loading = false;
  String? _error;

  // Resultado
  bool _seRealizo = true;
  bool _lograronCumbre = true;

  // Horarios
  String? _horaSalidaClub;
  String? _horaLlegadaMontana;
  String? _horaCumbre;
  String? _horaInicioDescenso;
  String? _horaLlegadaAutos;
  String? _horaRegresoClub;

  // Narrativa
  final _condicionesCtrl = TextEditingController();
  final _cronicaCtrl = TextEditingController();
  final _observacionesCtrl = TextEditingController();
  final _comentariosCtrl = TextEditingController();

  // Segmentos de viaje
  final List<_SegmentoState> _segmentos = [_SegmentoState()];

  // Guía
  bool _alquiloGuia = false;
  String? _guiaSocioId; // UUID del socio que actuó como guía
  final _costoGuiaCtrl = TextEditingController();

  // Refugio
  bool _alquiloRefugio = false;
  final _nombreRefugioCtrl = TextEditingController();
  final _costoRefugioCtrl = TextEditingController();

  // Camping
  bool _acampo = false;
  final _nombreCampingCtrl = TextEditingController();
  final _costoCampingCtrl = TextEditingController();

  // Autos
  String? _dondeAutos;
  final _autosDescripcionCtrl = TextEditingController();

  // Costos
  final _costoTotalCtrl = TextEditingController();
  final _costoPorPersonaCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    final existing = widget.existingInforme;
    if (existing == null) return;
    _seRealizo = existing.seRealizo ?? true;
    _lograronCumbre = existing.lograronCumbre ?? true;
    _horaSalidaClub = existing.horaSalidaClub;
    _horaLlegadaMontana = existing.horaLlegadaMontana;
    _horaCumbre = existing.horaCumbre;
    _horaInicioDescenso = existing.horaInicioDescenso;
    _horaLlegadaAutos = existing.horaLlegadaAutos;
    _horaRegresoClub = existing.horaRegresoClub;
    _condicionesCtrl.text = existing.condicionesMeteorologicas ?? '';
    _cronicaCtrl.text = existing.cronica ?? '';
    _observacionesCtrl.text = existing.observaciones ?? '';
    _comentariosCtrl.text = existing.comentariosVarios ?? '';
    _alquiloGuia = existing.alquiloGuia ?? false;
    _guiaSocioId = existing.guiaSocioId;
    if (existing.costoGuia != null) {
      _costoGuiaCtrl.text = existing.costoGuia.toString();
    }
    _alquiloRefugio = existing.alquiloRefugio ?? false;
    _nombreRefugioCtrl.text = existing.nombreRefugio ?? '';
    if (existing.costoRefugio != null) {
      _costoRefugioCtrl.text = existing.costoRefugio.toString();
    }
    _acampo = existing.acampo ?? false;
    _nombreCampingCtrl.text = existing.nombreCamping ?? '';
    if (existing.costoCamping != null) {
      _costoCampingCtrl.text = existing.costoCamping.toString();
    }
    _dondeAutos = existing.dondeAutos;
    _autosDescripcionCtrl.text = existing.autosDescripcion ?? '';
    if (existing.costoTotal != null) {
      _costoTotalCtrl.text = existing.costoTotal.toString();
    }
    if (existing.costoPorPersona != null) {
      _costoPorPersonaCtrl.text = existing.costoPorPersona.toString();
    }
    if (existing.tramos.isNotEmpty) {
      _segmentos
        ..clear()
        ..addAll(existing.tramos.map(_SegmentoState.fromTramo));
    }
  }

  static const _tiposTransporte = {
    'CAMIONETA': 'Camioneta',
    'FURGONETA': 'Furgoneta',
    'BUS_MEDIANO': 'Bus mediano',
    'BUS_GRANDE': 'Bus grande',
  };

  static const _dondeAutosOpts = {
    'NO_AUTOS': 'No se dejó autos',
    'PARQUEADERO_SEGURO': 'Parqueadero — seguro',
    'PARQUEADERO_INSEGURO': 'Parqueadero — inseguro',
    'BASE_MONTANA': 'En la base de la montaña',
    'CALLE_SEGURO': 'Calle — seguro',
    'CALLE_INSEGURO': 'Calle — inseguro',
  };

  static const _dondeAutosRequiereDescripcion = {
    'PARQUEADERO_SEGURO',
    'PARQUEADERO_INSEGURO',
    'BASE_MONTANA',
    'CALLE_SEGURO',
    'CALLE_INSEGURO',
  };

  @override
  void dispose() {
    _condicionesCtrl.dispose();
    _cronicaCtrl.dispose();
    _observacionesCtrl.dispose();
    _comentariosCtrl.dispose();
    _costoGuiaCtrl.dispose();
    _nombreRefugioCtrl.dispose();
    _costoRefugioCtrl.dispose();
    _nombreCampingCtrl.dispose();
    _costoCampingCtrl.dispose();
    _autosDescripcionCtrl.dispose();
    _costoTotalCtrl.dispose();
    _costoPorPersonaCtrl.dispose();
    for (final s in _segmentos) {
      s.dispose();
    }
    super.dispose();
  }

  Future<void> _pickTime(
      BuildContext context, String? current, ValueChanged<String?> onPick) async {
    final initial = current != null && current.isNotEmpty
        ? TimeOfDay(
            hour: int.parse(current.split(':')[0]),
            minute: int.parse(current.split(':')[1]),
          )
        : TimeOfDay.now();
    final picked = await showTimePicker(context: context, initialTime: initial);
    if (picked != null) {
      onPick('${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}');
    }
  }

  Future<void> _submit() async {
    for (var i = 0; i < _segmentos.length; i++) {
      final s = _segmentos[i];
      if (s.origenCtrl.text.trim().isEmpty || s.destinoCtrl.text.trim().isEmpty) {
        setState(() => _error = 'El tramo ${i + 1} debe tener origen y destino.');
        return;
      }
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    final req = CreateInformeRequest(
      seRealizo: _seRealizo,
      lograronCumbre: _lograronCumbre,
      alquiloGuia: _alquiloGuia,
      guiaSocioId: _alquiloGuia ? null : _guiaSocioId,
      alquiloRefugio: _alquiloRefugio,
      acampo: _acampo,
      segmentos: _segmentos.map((s) => s.toRequest()).toList(),
      horaSalidaClub: _horaSalidaClub,
      horaLlegadaMontana: _horaLlegadaMontana,
      horaCumbre: _horaCumbre,
      horaInicioDescenso: _horaInicioDescenso,
      horaLlegadaAutos: _horaLlegadaAutos,
      horaRegresoClub: _horaRegresoClub,
      condicionesMeteorologicas: _condicionesCtrl.text.trim().isEmpty
          ? null
          : _condicionesCtrl.text.trim(),
      cronica: _cronicaCtrl.text.trim().isEmpty ? null : _cronicaCtrl.text.trim(),
      observaciones: _observacionesCtrl.text.trim().isEmpty
          ? null
          : _observacionesCtrl.text.trim(),
      comentariosVarios: _comentariosCtrl.text.trim().isEmpty
          ? null
          : _comentariosCtrl.text.trim(),
      costoGuia: _alquiloGuia && _costoGuiaCtrl.text.trim().isNotEmpty
          ? double.tryParse(_costoGuiaCtrl.text.trim())
          : null,
      costoTotal: _costoTotalCtrl.text.trim().isNotEmpty
          ? double.tryParse(_costoTotalCtrl.text.trim())
          : null,
      costoPorPersona: _costoPorPersonaCtrl.text.trim().isNotEmpty
          ? double.tryParse(_costoPorPersonaCtrl.text.trim())
          : null,
      nombreRefugio: _alquiloRefugio && _nombreRefugioCtrl.text.trim().isNotEmpty
          ? _nombreRefugioCtrl.text.trim()
          : null,
      costoRefugio: _alquiloRefugio && _costoRefugioCtrl.text.trim().isNotEmpty
          ? double.tryParse(_costoRefugioCtrl.text.trim())
          : null,
      nombreCamping: _acampo && _nombreCampingCtrl.text.trim().isNotEmpty
          ? _nombreCampingCtrl.text.trim()
          : null,
      costoCamping: _acampo && _costoCampingCtrl.text.trim().isNotEmpty
          ? double.tryParse(_costoCampingCtrl.text.trim())
          : null,
      dondeAutos: _dondeAutos,
      autosDescripcion: _autosDescripcionCtrl.text.trim().isEmpty
          ? null
          : _autosDescripcionCtrl.text.trim(),
    );

    try {
      final repo = ref.read(informesRepositoryProvider);
      if (widget.existingInforme != null) {
        await repo.updateInforme(widget.salidaId, req);
      } else {
        await repo.createInforme(widget.salidaId, req);
      }
      widget.onSaved();
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = unwrapDio(e).toString();
        });
      }
    }
  }

  // ─── Build ──────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.sidebar,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.existingInforme != null
                ? 'Editar informe'
                : 'Informe de salida'),
            if (widget.salidaNombre != null && widget.salidaNombre!.isNotEmpty)
              Text(
                widget.salidaNombre!,
                style:
                    AppTextStyles.bodySmall.copyWith(color: AppColors.mutedFg),
              ),
          ],
        ),
      ),
      body: SafeArea(
        child: CustomScrollView(
          slivers: [
            SliverPadding(
              padding: const EdgeInsets.all(16),
              sliver: SliverList(
                delegate: SliverChildListDelegate([
                  if (_error != null) ...[
                    _ErrorBanner(_error!),
                    const SizedBox(height: 12),
                  ],
                  _SectionHeader('Resultado'),
                  const SizedBox(height: 8),
                  _ResultadoSection(
                    seRealizo: _seRealizo,
                    lograronCumbre: _lograronCumbre,
                    onSeRealizoChanged: (v) => setState(() => _seRealizo = v),
                    onLograronCumbreChanged: (v) =>
                        setState(() => _lograronCumbre = v),
                  ),
                  const SizedBox(height: 20),
                  _SectionHeader('Horarios'),
                  const SizedBox(height: 8),
                  _HorariosSection(
                    horaSalidaClub: _horaSalidaClub,
                    horaLlegadaMontana: _horaLlegadaMontana,
                    horaCumbre: _horaCumbre,
                    horaInicioDescenso: _horaInicioDescenso,
                    horaLlegadaAutos: _horaLlegadaAutos,
                    horaRegresoClub: _horaRegresoClub,
                    onPick: _pickTime,
                    onChanged: (key, v) => setState(() {
                      switch (key) {
                        case 'salidaClub':
                          _horaSalidaClub = v;
                        case 'llegadaMontana':
                          _horaLlegadaMontana = v;
                        case 'cumbre':
                          _horaCumbre = v;
                        case 'inicioDescenso':
                          _horaInicioDescenso = v;
                        case 'llegadaAutos':
                          _horaLlegadaAutos = v;
                        case 'regresoClub':
                          _horaRegresoClub = v;
                      }
                    }),
                  ),
                  const SizedBox(height: 20),
                  _SectionHeader('Condiciones y narrativa'),
                  const SizedBox(height: 8),
                  AppCard(
                    child: Column(
                      children: [
                        _LabeledField(
                          label: 'Condiciones meteorológicas',
                          child: TextField(
                            controller: _condicionesCtrl,
                            decoration: const InputDecoration(
                              hintText: 'Ej: Soleado, viento moderado...',
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        _LabeledField(
                          label: 'Crónica de la salida',
                          child: TextField(
                            controller: _cronicaCtrl,
                            maxLines: 4,
                            decoration: const InputDecoration(
                              hintText: 'Narra cómo fue la salida...',
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        _LabeledField(
                          label: 'Observaciones',
                          child: TextField(
                            controller: _observacionesCtrl,
                            maxLines: 3,
                            decoration: const InputDecoration(
                              hintText: 'Incidencias, aspectos técnicos...',
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        _LabeledField(
                          label: 'Comentarios varios',
                          child: TextField(
                            controller: _comentariosCtrl,
                            maxLines: 2,
                            decoration: const InputDecoration(
                              hintText: 'Cualquier otro comentario...',
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  _SectionHeader('Segmentos de viaje'),
                  const SizedBox(height: 8),
                  ..._buildSegmentos(),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: () => setState(() {
                      final last = _segmentos.last;
                      final next = _SegmentoState();
                      next.origenCtrl.text = last.destinoCtrl.text;
                      next.destinoCtrl.text = 'Club Sadday';
                      _segmentos.add(next);
                    }),
                    icon: const Icon(Icons.add, size: 18),
                    label: const Text('Añadir tramo'),
                  ),
                  const SizedBox(height: 20),
                  _SectionHeader('Guía'),
                  const SizedBox(height: 8),
                  AppCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          title: const Text('¿Se contrató guía externo?'),
                          value: _alquiloGuia,
                          onChanged: (v) => setState(() {
                            _alquiloGuia = v;
                            if (!v) {
                              _costoGuiaCtrl.clear();
                            } else {
                              _guiaSocioId = null;
                            }
                          }),
                        ),
                        if (_alquiloGuia) ...[
                          const SizedBox(height: 8),
                          _LabeledField(
                            label: 'Costo del guía (\$)',
                            child: _MoneyTextField(controller: _costoGuiaCtrl),
                          ),
                        ],
                        if (!_alquiloGuia) ...[
                          const SizedBox(height: 8),
                          _GuiaSocioSelector(
                            salidaId: widget.salidaId,
                            selectedSocioId: _guiaSocioId,
                            onChanged: (id) =>
                                setState(() => _guiaSocioId = id),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  _SectionHeader('Alojamiento'),
                  const SizedBox(height: 8),
                  AppCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          title: const Text('¿Se alquiló refugio?'),
                          value: _alquiloRefugio,
                          onChanged: (v) => setState(() {
                            _alquiloRefugio = v;
                            if (!v) {
                              _nombreRefugioCtrl.clear();
                              _costoRefugioCtrl.clear();
                            }
                          }),
                        ),
                        if (_alquiloRefugio) ...[
                          const SizedBox(height: 8),
                          _LabeledField(
                            label: 'Nombre del refugio',
                            child: TextField(
                              controller: _nombreRefugioCtrl,
                              decoration: const InputDecoration(
                                hintText: 'Ej: Refugio Whymper',
                              ),
                            ),
                          ),
                          const SizedBox(height: 8),
                          _LabeledField(
                            label: 'Costo del refugio (\$)',
                            child: _MoneyTextField(controller: _costoRefugioCtrl),
                          ),
                        ],
                        const Divider(height: 24, color: AppColors.border),
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          title: const Text('¿Se acampó?'),
                          value: _acampo,
                          onChanged: (v) => setState(() {
                            _acampo = v;
                            if (!v) {
                              _nombreCampingCtrl.clear();
                              _costoCampingCtrl.clear();
                            }
                          }),
                        ),
                        if (_acampo) ...[
                          const SizedBox(height: 8),
                          _LabeledField(
                            label: 'Nombre del camping',
                            child: TextField(
                              controller: _nombreCampingCtrl,
                              decoration: const InputDecoration(
                                hintText: 'Ej: Camping Chimborazo',
                              ),
                            ),
                          ),
                          const SizedBox(height: 8),
                          _LabeledField(
                            label: 'Costo del camping (\$)',
                            child: _MoneyTextField(controller: _costoCampingCtrl),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  _SectionHeader('¿Dónde se dejaron los autos?'),
                  const SizedBox(height: 8),
                  AppCard(
                    child: Column(
                      children: [
                        DropdownButton<String>(
                          value: _dondeAutos,
                          isExpanded: true,
                          dropdownColor: AppColors.card,
                          underline: const SizedBox.shrink(),
                          hint: Text(
                            'Selecciona una opción...',
                            style: AppTextStyles.bodyMedium
                                .copyWith(color: AppColors.mutedFg),
                          ),
                          items: _dondeAutosOpts.entries
                              .map((e) => DropdownMenuItem(
                                    value: e.key,
                                    child: Text(e.value,
                                        style: AppTextStyles.bodyMedium),
                                  ))
                              .toList(),
                          onChanged: (v) => setState(() {
                            _dondeAutos = v;
                            if (v == null || v == 'NO_AUTOS') {
                              _autosDescripcionCtrl.clear();
                            }
                          }),
                        ),
                        if (_dondeAutos != null &&
                            _dondeAutosRequiereDescripcion
                                .contains(_dondeAutos)) ...[
                          const SizedBox(height: 12),
                          _LabeledField(
                            label: 'Dirección o descripción del lugar',
                            child: TextField(
                              controller: _autosDescripcionCtrl,
                              decoration: const InputDecoration(
                                hintText:
                                    'Ej: Av. Colón y 10 de Agosto, parqueadero El Centro',
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  _SectionHeader('Costos totales'),
                  const SizedBox(height: 8),
                  AppCard(
                    child: Column(
                      children: [
                        _LabeledField(
                          label: 'Costo total del viaje (\$)',
                          child: _MoneyTextField(
                            controller: _costoTotalCtrl,
                            hint: '0.00',
                          ),
                        ),
                        const SizedBox(height: 12),
                        _LabeledField(
                          label: 'Costo por persona (\$)',
                          child: _MoneyTextField(
                            controller: _costoPorPersonaCtrl,
                            hint: '0.00',
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 32),
                  AppButton(
                    label: 'Guardar informe',
                    fullWidth: true,
                    loading: _loading,
                    icon: Icons.check,
                    onPressed: _loading ? null : _submit,
                  ),
                  const SizedBox(height: 24),
                ]),
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _buildSegmentos() {
    return List.generate(_segmentos.length, (i) {
      final s = _segmentos[i];
      return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(Icons.route_outlined,
                      color: AppColors.mutedFg, size: 18),
                  const SizedBox(width: 8),
                  Text('Tramo ${i + 1}',
                      style: AppTextStyles.labelSmall
                          .copyWith(color: AppColors.mutedFg)),
                  const Spacer(),
                  if (_segmentos.length > 1)
                    IconButton(
                      icon: const Icon(Icons.delete_outline,
                          color: AppColors.destructive, size: 20),
                      onPressed: () => setState(() {
                        s.dispose();
                        _segmentos.removeAt(i);
                      }),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: _LabeledField(
                      label: 'Origen',
                      child: TextField(
                        controller: s.origenCtrl,
                        onChanged: (_) => setState(() {}),
                        decoration:
                            const InputDecoration(hintText: 'Ej: Club Sadday'),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _LabeledField(
                      label: 'Destino',
                      child: TextField(
                        controller: s.destinoCtrl,
                        onChanged: (_) => setState(() {}),
                        decoration: const InputDecoration(
                            hintText: 'Ej: Parqueadero base'),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: Text('¿Se alquiló transporte externo?',
                    style: AppTextStyles.bodySmall),
                value: s.alquiloTransporte,
                onChanged: (v) => setState(() {
                  s.alquiloTransporte = v;
                  if (!v) {
                    s.tipoTransporte = null;
                    s.costoCtrl.clear();
                  }
                }),
              ),
              if (s.alquiloTransporte) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: _LabeledField(
                        label: 'Tipo de vehículo',
                        child: DropdownButton<String>(
                          value: s.tipoTransporte,
                          isExpanded: true,
                          dropdownColor: AppColors.card,
                          underline: const SizedBox.shrink(),
                          hint: Text(
                            'Tipo...',
                            style: AppTextStyles.bodySmall
                                .copyWith(color: AppColors.mutedFg),
                          ),
                          items: _tiposTransporte.entries
                              .map((e) => DropdownMenuItem(
                                    value: e.key,
                                    child: Text(e.value,
                                        style: AppTextStyles.bodySmall),
                                  ))
                              .toList(),
                          onChanged: (v) =>
                              setState(() => s.tipoTransporte = v),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _LabeledField(
                        label: 'Costo total (\$)',
                        child: _MoneyTextField(controller: s.costoCtrl),
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      );
    });
  }
}

// ─── Sub-widgets ─────────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);
  final String title;

  @override
  Widget build(BuildContext context) => Text(
        title,
        style: AppTextStyles.titleSmall.copyWith(
          color: AppColors.mutedFg,
          fontWeight: FontWeight.w600,
        ),
      );
}

class _LabeledField extends StatelessWidget {
  const _LabeledField({required this.label, required this.child});
  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style:
                  AppTextStyles.labelSmall.copyWith(color: AppColors.mutedFg)),
          const SizedBox(height: 4),
          child,
        ],
      );
}

class _MoneyTextField extends StatelessWidget {
  const _MoneyTextField({required this.controller, this.hint = '0.00'});
  final TextEditingController controller;
  final String hint;

  @override
  Widget build(BuildContext context) => TextField(
        controller: controller,
        keyboardType:
            const TextInputType.numberWithOptions(decimal: true),
        inputFormatters: [
          FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d{0,2}')),
        ],
        decoration: InputDecoration(hintText: hint),
      );
}

class _ResultadoSection extends StatelessWidget {
  const _ResultadoSection({
    required this.seRealizo,
    required this.lograronCumbre,
    required this.onSeRealizoChanged,
    required this.onLograronCumbreChanged,
  });

  final bool seRealizo;
  final bool lograronCumbre;
  final ValueChanged<bool> onSeRealizoChanged;
  final ValueChanged<bool> onLograronCumbreChanged;

  @override
  Widget build(BuildContext context) => AppCard(
        child: Column(
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('¿La salida se realizó?'),
                _BoolToggle(
                  value: seRealizo,
                  onChanged: onSeRealizoChanged,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('¿Lograron la cumbre?'),
                _BoolToggle(
                  value: lograronCumbre,
                  onChanged: onLograronCumbreChanged,
                ),
              ],
            ),
          ],
        ),
      );
}

class _BoolToggle extends StatelessWidget {
  const _BoolToggle({required this.value, required this.onChanged});
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _ToggleChip(
            label: 'Sí',
            selected: value,
            onTap: () => onChanged(true),
          ),
          const SizedBox(width: 8),
          _ToggleChip(
            label: 'No',
            selected: !value,
            onTap: () => onChanged(false),
          ),
        ],
      );
}

class _ToggleChip extends StatelessWidget {
  const _ToggleChip(
      {required this.label, required this.selected, required this.onTap});
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          decoration: BoxDecoration(
            color: selected
                ? AppColors.primary
                : AppColors.secondary,
            borderRadius: BorderRadius.circular(6),
          ),
          child: Text(
            label,
            style: TextStyle(
              color: selected ? AppColors.foreground : AppColors.mutedFg,
              fontSize: 13,
              fontWeight:
                  selected ? FontWeight.w600 : FontWeight.normal,
            ),
          ),
        ),
      );
}

class _HorariosSection extends StatelessWidget {
  const _HorariosSection({
    required this.horaSalidaClub,
    required this.horaLlegadaMontana,
    required this.horaCumbre,
    required this.horaInicioDescenso,
    required this.horaLlegadaAutos,
    required this.horaRegresoClub,
    required this.onPick,
    required this.onChanged,
  });

  final String? horaSalidaClub;
  final String? horaLlegadaMontana;
  final String? horaCumbre;
  final String? horaInicioDescenso;
  final String? horaLlegadaAutos;
  final String? horaRegresoClub;
  final Future<void> Function(
      BuildContext, String?, ValueChanged<String?>) onPick;
  final void Function(String key, String? value) onChanged;

  @override
  Widget build(BuildContext context) {
    final fields = [
      ('salidaClub', 'Salida del club', horaSalidaClub),
      ('llegadaMontana', 'Llegada a la montaña', horaLlegadaMontana),
      ('cumbre', 'Hora de cumbre', horaCumbre),
      ('inicioDescenso', 'Inicio descenso', horaInicioDescenso),
      ('llegadaAutos', 'Llegada a autos', horaLlegadaAutos),
      ('regresoClub', 'Regreso al club', horaRegresoClub),
    ];

    return AppCard(
      child: Wrap(
        spacing: 12,
        runSpacing: 12,
        children: fields.map((f) {
          return SizedBox(
            width: (MediaQuery.of(context).size.width - 80) / 2,
            child: GestureDetector(
              onTap: () => onPick(
                context,
                f.$3,
                (v) => onChanged(f.$1, v),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(f.$2,
                      style: AppTextStyles.labelSmall
                          .copyWith(color: AppColors.mutedFg)),
                  const SizedBox(height: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 10),
                    decoration: BoxDecoration(
                      color: AppColors.secondary,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.access_time_outlined,
                            size: 16, color: AppColors.mutedFg),
                        const SizedBox(width: 8),
                        Text(
                          f.$3 ?? '--:--',
                          style: TextStyle(
                            color: f.$3 != null
                                ? AppColors.foreground
                                : AppColors.mutedFg,
                            fontSize: 14,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}

// ─── Selector de guía socio ──────────────────────────────────────────────────

/// Cuando no se contrató guía externo, permite seleccionar qué socio participante
/// actuó como guía de la salida.
class _GuiaSocioSelector extends ConsumerWidget {
  const _GuiaSocioSelector({
    required this.salidaId,
    required this.selectedSocioId,
    required this.onChanged,
  });

  final String salidaId;
  final String? selectedSocioId;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final salidaAsync = ref.watch(salidaDetailProvider(salidaId));

    return salidaAsync.when(
      loading: () => const SizedBox(
        height: 40,
        child: Center(
          child: SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      ),
      error: (e, s) => const SizedBox.shrink(),
      data: (salida) {
        final activos = salida.participantes
            .where((p) =>
                p.estadoInscripcion == 'INSCRITO' ||
                p.estadoInscripcion == 'CONFIRMADO')
            .toList();

        if (activos.isEmpty) return const SizedBox.shrink();

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _LabeledField(
              label: 'Socio que actuó como guía (opcional)',
              child: DropdownButton<String>(
                value: selectedSocioId,
                isExpanded: true,
                dropdownColor: AppColors.card,
                underline: const SizedBox.shrink(),
                hint: Text(
                  'Ninguno / No aplica',
                  style: AppTextStyles.bodyMedium
                      .copyWith(color: AppColors.mutedFg),
                ),
                items: [
                  DropdownMenuItem<String>(
                    value: null,
                    child: Text('Ninguno',
                        style: AppTextStyles.bodyMedium
                            .copyWith(color: AppColors.mutedFg)),
                  ),
                  ...activos.map(
                    (p) => DropdownMenuItem<String>(
                      value: p.socioId,
                      child: Text(p.nombre, style: AppTextStyles.bodyMedium),
                    ),
                  ),
                ],
                onChanged: onChanged,
              ),
            ),
            if (selectedSocioId != null) ...[
              const SizedBox(height: 4),
              Text(
                'Se registrará como "Guía" en las dignidades de la salida.',
                style: AppTextStyles.labelSmall
                    .copyWith(color: AppColors.mutedFg),
              ),
            ],
          ],
        );
      },
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner(this.message);
  final String message;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.destructive.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
              color: AppColors.destructive.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            const Icon(Icons.error_outline,
                color: AppColors.destructive, size: 18),
            const SizedBox(width: 8),
            Expanded(
              child: Text(message,
                  style: AppTextStyles.bodySmall
                      .copyWith(color: AppColors.destructive)),
            ),
          ],
        ),
      );
}
